package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import java.net.URL
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.getWellKnown
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.persistering.handleRecivedMessage
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smmanuell-backend")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
        mqUsername = getFileAsString("/secrets/default/mqUsername"),
        mqPassword = getFileAsString("/secrets/default/mqPassword"),
        oidcWellKnownUri = getFileAsString("/secrets/default/oidcWellKnownUri"),
        syfosmmanuellBackendClientId = getFileAsString("/secrets/azuread/syfosmmanuell-backend/client_id")
    )

    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val manuellOppgaveService = ManuellOppgaveService(database)

    val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    val producerProperties =
        kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${env.applicationName}-consumer",
        valueDeserializer = StringDeserializer::class
    )

    val kafkaproducerApprec = KafkaProducer<String, Apprec>(producerProperties)
    val kafkaproducerreceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val kafkaproducervalidationResult = KafkaProducer<String, ValidationResult>(producerProperties)

    val connection = connectionFactory(env).createConnection(vaultSecrets.mqUsername, vaultSecrets.mqPassword)
    connection.start()
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val syfoserviceProducer = session.producerForQueue(env.syfoserviceQueueName)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }

    val httpClient = HttpClient(Apache, config)

    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)
    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(env.syfoTilgangsKontrollClientUrl, httpClient)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        manuellOppgaveService,
        kafkaproducerApprec,
        env.sm2013Apprec,
        kafkaproducerreceivedSykmelding,
        env.sm2013AutomaticHandlingTopic,
        env.sm2013InvalidHandlingTopic,
        env.sm2013BehandlingsUtfallToipic,
        kafkaproducervalidationResult,
        env.syfoserviceQueueName,
        session,
        syfoserviceProducer,
        oppgaveClient,
        vaultSecrets,
        jwkProvider,
        wellKnown.issuer,
        syfoTilgangsKontrollClient
    )

    ApplicationServer(applicationEngine, connection).start()

    applicationState.ready = true

    launchListeners(
        applicationState,
        env,
        consumerProperties,
        database,
        oppgaveClient
    )
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                fields(e.loggingMeta), e.cause
            )
        } finally {
            applicationState.alive = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    consumerProperties: Properties,
    database: Database,
    oppgaveClient: OppgaveClient
) {
    createListener(applicationState) {
        val kafkaconsumermanuellOppgave = KafkaConsumer<String, String>(consumerProperties)

        kafkaconsumermanuellOppgave.subscribe(listOf(env.syfoSmManuellTopic))
        blockingApplicationLogic(
            applicationState,
            kafkaconsumermanuellOppgave,
            database,
            oppgaveClient
        )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    database: Database,
    oppgaveClient: OppgaveClient
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

            handleRecivedMessage(
                receivedManuellOppgave,
                loggingMeta,
                database,
                oppgaveClient
            )
        }
        delay(100)
    }
}
