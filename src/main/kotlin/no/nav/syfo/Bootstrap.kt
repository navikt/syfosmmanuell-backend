package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import java.net.URL
import java.time.Duration
import java.util.concurrent.TimeUnit
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
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.clients.KafkaConsumers
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.Database
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.handleReceivedMessage
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.vault.RenewVaultService
import org.apache.kafka.clients.consumer.KafkaConsumer
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
            serviceuserUsername = getFileAsString(env.serviceuserUsernamePath),
            serviceuserPassword = getFileAsString(env.serviceuserPasswordPath),
            oidcWellKnownUri = getFileAsString(env.oidcWellKnownUriPath),
            syfosmmanuellBackendClientId = getFileAsString(env.syfosmmanuellBackendClientIdPath),
            syfosmmanuellBackendClientSecret = getFileAsString(env.syfosmmanuellBackendClientSecretPath)
    )

    val wellKnown = getWellKnown(vaultSecrets.oidcWellKnownUri)
    val jwkProvider = JwkProviderBuilder(URL(wellKnown.jwks_uri))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val applicationState = ApplicationState()

    val kafkaProducers = KafkaProducers(env, vaultSecrets)
    val kafkaConsumers = KafkaConsumers(env, vaultSecrets)
    val httpClients = HttpClients(env, vaultSecrets)
    val oppgaveService = OppgaveService(httpClients.oppgaveClient)

    val manuellOppgaveService = ManuellOppgaveService(database,
            httpClients.syfoTilgangsKontrollClient,
            kafkaProducers, oppgaveService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        manuellOppgaveService,
        vaultSecrets,
        jwkProvider,
        wellKnown.issuer,
        httpClients.syfoTilgangsKontrollClient
    )

    ApplicationServer(applicationEngine).start()

    applicationState.ready = true

    if (!env.developmentMode) {
        RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()
    }

    launchListeners(
        applicationState,
        env,
        kafkaConsumers,
        database,
        oppgaveService
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
            applicationState.ready = false
        }
    }

@KtorExperimentalAPI
fun launchListeners(
    applicationState: ApplicationState,
    env: Environment,
    kafkaConsumers: KafkaConsumers,
    database: Database,
    oppgaveService: OppgaveService
) {
    createListener(applicationState) {
        val kafkaConsumerManuellOppgave = kafkaConsumers.kafkaConsumerManuellOppgave

        kafkaConsumerManuellOppgave.subscribe(listOf(env.syfoSmManuellTopic))
        blockingApplicationLogic(
            applicationState,
            kafkaConsumerManuellOppgave,
            database,
            oppgaveService
        )
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, String>,
    database: Database,
    oppgaveService: OppgaveService
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

            handleReceivedMessage(
                receivedManuellOppgave,
                loggingMeta,
                database,
                oppgaveService
            )
        }
        delay(100)
    }
}
