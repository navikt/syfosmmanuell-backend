package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import io.mockk.mockk
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.Test
import javax.jms.MessageProducer
import javax.jms.Session

internal class SendVurderingManuellOppgaveTest{
    val database = TestDB()

    val manuellOppgaveService = ManuellOppgaveService(database)

    val manuelloppgaveId = "1314"

    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
        validationResult = ValidationResult(Status.OK, emptyList()),
        apprec = objectMapper.readValue(
            Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
                Charsets.UTF_8
            )
        ),
        behandlendeEnhet = "1234"
    )
    val oppgaveid = 308076319


    val sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic"
    val sm2013InvalidHandlingTopic = "sm2013InvalidHandlingTopic"
    val sm2013BehandlingsUtfallToipic = "sm2013BehandlingsUtfallToipic"
    val sm2013ApprecTopicName = "sm2013ApprecTopicName"


    val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>()
    val kafkaproducerreceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>()
    val kafkaproducervalidationResult = mockk<KafkaProducer<String, ValidationResult>>()


    val syfoserviceQueueName = "syfoserviceQueueName"
    private val session = mockk<Session>()
    private val syfoserviceProducer = mockk<MessageProducer>()
    @KtorExperimentalAPI
    private val oppgaveClient = mockk<OppgaveClient>()

    @KtorExperimentalAPI
    @Test
    internal fun `Skal returnere InternalServerError, naar oppdatering av manuelloppgave sitt ValidationResults feilet`() {
        with(TestApplicationEngine()) {
            start()

            database.opprettManuellOppgave(manuellOppgave, "1354", oppgaveid)

            application.routing { sendVurderingManuellOppgave(
                manuellOppgaveService,
                kafkaproducerApprec,
                sm2013ApprecTopicName,
                kafkaproducerreceivedSykmelding,
                sm2013AutomaticHandlingTopic,
                sm2013InvalidHandlingTopic,
                sm2013BehandlingsUtfallToipic,
                kafkaproducervalidationResult,
                syfoserviceQueueName,
                session,
                syfoserviceProducer,
                oppgaveClient
                ) }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }


            val validationResult = ValidationResult(status = Status.INVALID, ruleHits = listOf(
                RuleInfo(ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                    messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                    messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med " +
                            "autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                    ruleStatus = Status.INVALID
                )))

            with(handleRequest(HttpMethod.Put, "/api/v1/vurderingmanuelloppgave/"){
                addHeader("oppgaveid", "1234444")
                setBody(objectMapper.writeValueAsString(validationResult))
            }) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual null
            }
        }
    }

}