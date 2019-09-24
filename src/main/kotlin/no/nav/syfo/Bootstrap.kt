package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.erOpprettManuellOppgave
import no.nav.syfo.persistering.opprettManuellOppgave
import no.nav.syfo.service.FindNAVKontorService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.vault.Vault
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")

val backgroundTasksContext = Executors.newFixedThreadPool(4).asCoroutineDispatcher() + MDCContext()

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "0393"

@KtorExperimentalAPI
fun main() = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()

    launch(backgroundTasksContext) {
        try {
            Vault.renewVaultTokenTask(applicationState)
        } finally {
            applicationState.ready = false
        }
    }

    launch(backgroundTasksContext) {
        try {
            vaultCredentialService.runRenewCredentialsTask { applicationState.ready }
        } finally {
            applicationState.ready = false
        }
    }

    val manuellOppgaveService = ManuellOppgaveService(database)

    val kafkaBaseConfig = loadBaseConfig(env, credentials)
    val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val kafkaproducerApprec = KafkaProducer<String, Apprec>(producerProperties)
    val kafkaproducerreceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        manuellOppgaveService,
        kafkaproducerApprec,
        env.sm2013Apprec,
        kafkaproducerreceivedSykmelding,
        env.sm2013AutomaticHandlingTopic,
        env.sm2013InvalidHandlingTopic)
    val applicationServer = ApplicationServer(applicationEngine)

    applicationServer.start()

    val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    launchListeners(
        applicationState,
        env,
        consumerProperties,
        database,
        personV3,
        arbeidsfordelingV1)
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}",
                fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.ready = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    consumerProperties: Properties,
    database: Database,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1
) {
    val kafkaconsumermanuellOppgave = KafkaConsumer<String, String>(consumerProperties)

    kafkaconsumermanuellOppgave.subscribe(
        listOf(env.syfoSmManuellTopic)
    )
    createListener(applicationState) {

                blockingApplicationLogic(
                    applicationState,
                    kafkaconsumermanuellOppgave,
                    database,
                    personV3,
                    arbeidsfordelingV1)
    }

    applicationState.ready = true
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    database: Database,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1
) {
    while (applicationState.ready) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
            val receivedManuellOppgave: ManuellOppgave = objectMapper.readValue(consumerRecord.value())
            val loggingMeta = LoggingMeta(
                mottakId = receivedManuellOppgave.receivedSykmelding.navLogId,
                orgNr = receivedManuellOppgave.receivedSykmelding.legekontorOrgNr,
                msgId = receivedManuellOppgave.receivedSykmelding.msgId,
                sykmeldingId = receivedManuellOppgave.receivedSykmelding.sykmelding.id
            )

            handleMessage(
                receivedManuellOppgave,
                loggingMeta,
                database,
                personV3,
                arbeidsfordelingV1)
        }
        delay(100)
    }
}

@KtorExperimentalAPI
suspend fun handleMessage(
    manuellOppgave: ManuellOppgave,
    loggingMeta: LoggingMeta,
    database: Database,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell oppgave, {}", fields(loggingMeta))

        if (database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
            log.error("Manuell oppgave med id {} allerede lagret i databasen, {}",
                manuellOppgave.receivedSykmelding.sykmelding.id, fields(loggingMeta)
            )
        } else {
           /* val findNAVKontorService = FindNAVKontorService(manuellOppgave.receivedSykmelding, personV3, arbeidsfordelingV1, loggingMeta)
            val behandlendeEnhet = findNAVKontorService.finnBehandlendeEnhet()

          */

            database.opprettManuellOppgave(manuellOppgave, NAV_OPPFOLGING_UTLAND_KONTOR_NR)
            log.info("Manuell oppgave lagret i databasen, for tildeltEnhetsnr: {}, {}", NAV_OPPFOLGING_UTLAND_KONTOR_NR, fields(loggingMeta))
            MESSAGE_STORED_IN_DB_COUNTER.inc()

            // TODO poste på modia hendelse topic, med manuell oppgaveid(manuellOppgave.receivedSykmelding.sykmelding.id)
            // TODO pasient fnr manuellOppgave.receivedSykmelding.personNrLege
        }
    }
}
