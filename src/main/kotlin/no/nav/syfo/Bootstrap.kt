package no.nav.syfo

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.clients.HttpClients
import no.nav.syfo.clients.KafkaConsumers
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.Database
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.MottattSykmeldingService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.TrackableException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smmanuell-backend")

@ExperimentalTime
@DelicateCoroutinesApi
fun main() {
    val env = Environment()

    val jwkProvider = JwkProviderBuilder(URL(env.jwkKeysUrl))
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val database = Database(env)

    val applicationState = ApplicationState()

    val kafkaProducers = KafkaProducers(env)
    val kafkaConsumers = KafkaConsumers(env)
    val httpClients = HttpClients(env)
    val oppgaveService = OppgaveService(httpClients.oppgaveClient, kafkaProducers.kafkaProduceTaskProducer)

    val manuellOppgaveService = ManuellOppgaveService(
        database,
        httpClients.syfoTilgangsKontrollClient,
        kafkaProducers, oppgaveService
    )

    val authorizationService = AuthorizationService(
        httpClients.syfoTilgangsKontrollClient,
        httpClients.msGraphClient, database
    )

    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
        manuellOppgaveService,
        jwkProvider,
        env.jwtIssuer,
        authorizationService
    )

    val mottattSykmeldingService = MottattSykmeldingService(
        kafkaAivenConsumer = kafkaConsumers.kafkaAivenConsumerManuellOppgave,
        applicationState = applicationState,
        topicAiven = env.manuellTopic,
        database = database,
        oppgaveService = oppgaveService,
        manuellOppgaveService = manuellOppgaveService
    )

    applicationState.ready = true
    /*
    GlobalScope.launch {
        applicationState.ready = true

        createListener(applicationState) {
            mottattSykmeldingService.startAivenConsumer()
        }
    }

     */

    ApplicationServer(applicationEngine, applicationState).start()
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch(Dispatchers.IO) {
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
