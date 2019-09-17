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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.persistering.erOpprettManuellOppgave
import no.nav.syfo.persistering.opprettManuellOppgave
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)
    val applicationState = ApplicationState()

    val applicationEngine = createApplicationEngine(env, applicationState)
    val applicationServer = ApplicationServer(applicationEngine)

    applicationServer.start()

    val kafkaBaseConfig = loadBaseConfig(env, credentials)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)

    launchListeners(
        applicationState,
        env,
        consumerProperties,
        credentials,
        database)
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
    credentials: VaultCredentials,
    database: Database
) {
    val kafkaconsumermanuellOppgave = KafkaConsumer<String, String>(consumerProperties)

    kafkaconsumermanuellOppgave.subscribe(
        listOf(env.syfoSmManuellTopic)
    )
    createListener(applicationState) {

                blockingApplicationLogic(applicationState, kafkaconsumermanuellOppgave, database)
    }

    applicationState.ready = true
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    database: Database
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

            handleMessage(receivedManuellOppgave, loggingMeta, database)
        }
        delay(100)
    }
}

@KtorExperimentalAPI
suspend fun handleMessage(
    manuellOppgave: ManuellOppgave,
    loggingMeta: LoggingMeta,
    database: Database
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell oppgave, {}", fields(loggingMeta))

        if (database.connection.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
            log.error("Manuell oppgave med id {} allerede lagret i databasen, {}",
                manuellOppgave.receivedSykmelding.sykmelding.id, fields(loggingMeta)
            )
        } else {
            database.connection.opprettManuellOppgave(manuellOppgave)
            log.info("Manuell oppgave lagret i databasen, {}", fields(loggingMeta))
            MESSAGE_STORED_IN_DB_COUNTER.inc()
        }
    }
}
