package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.util.concurrent.Future
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveResponse
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.Test

internal class SendVurderingManuellOppgaveTest {
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
        )
    )
    val oppgaveid = 308076319

    val kafkaApprecProducer = mockk<KafkaProducers.Companion.KafkaApprecProducer>()
    val kafkaRecievedSykmeldingProducer = mockk<KafkaProducers.Companion.KafkaRecievedSykmeldingProducer>()
    val kafkaValidationResultProducer = mockk<KafkaProducers.Companion.KafkaValidationResultProducer>()

    val sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic"
    val sm2013InvalidHandlingTopic = "sm2013InvalidHandlingTopic"
    val sm2013BehandlingsUtfallTopic = "sm2013BehandlingsUtfallTopic"
    val sm2013ApprecTopicName = "sm2013ApprecTopicName"

    val textMessage = mockk<TextMessage>()

    val syfoserviceQueueName = "syfoserviceQueueName"
    private val session = mockk<Session>()
    private val syfoserviceProducer = mockk<MessageProducer>()
    @KtorExperimentalAPI
    private val oppgaveClient = mockk<OppgaveClient>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()

    @KtorExperimentalAPI
    @Test
    internal fun `Skal returnere InternalServerError, naar oppdatering av manuelloppgave sitt ValidationResults feilet`() {
        with(TestApplicationEngine()) {
            start()

            coEvery { kafkaValidationResultProducer.producer } returns mockk<KafkaProducer<String, ValidationResult>>()
            coEvery { kafkaValidationResultProducer.syfoserviceQueueName } returns "Foo"

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            application.routing {
                sendVurderingManuellOppgave(
                        manuellOppgaveService,
                        kafkaApprecProducer,
                        kafkaRecievedSykmeldingProducer,
                        kafkaValidationResultProducer,
                        session,
                        syfoserviceProducer,
                        oppgaveClient,
                        syfoTilgangsKontrollClient
                )
            }
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

            val validationResult = ValidationResult(
                status = Status.INVALID, ruleHits = listOf(
                    RuleInfo(
                        ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                        messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                        messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med " +
                                "autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                        ruleStatus = Status.INVALID
                    )
                )
            )

            with(handleRequest(HttpMethod.Put, "/api/v1/vurderingmanuelloppgave/21314") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                setBody(objectMapper.writeValueAsString(validationResult))
            }) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual null
            }
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `noConten oppdatering av manuelloppgave med status OK`() {
        with(TestApplicationEngine()) {
            start()

            coEvery { kafkaApprecProducer.producer } returns mockk()
            coEvery { kafkaApprecProducer.sm2013ApprecTopic } returns sm2013ApprecTopicName

            coEvery { kafkaValidationResultProducer.producer } returns mockk()
            coEvery { kafkaValidationResultProducer.syfoserviceQueueName } returns syfoserviceQueueName

            coEvery { kafkaRecievedSykmeldingProducer.producer } returns mockk()
            coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns sm2013AutomaticHandlingTopic
            coEvery { kafkaRecievedSykmeldingProducer.sm2013InvalidHandlingTopic } returns sm2013InvalidHandlingTopic
            coEvery { kafkaRecievedSykmeldingProducer.sm2013BehandlingsUtfallTopic } returns sm2013BehandlingsUtfallTopic

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            application.routing {
                sendVurderingManuellOppgave(
                        manuellOppgaveService,
                        kafkaApprecProducer,
                        kafkaRecievedSykmeldingProducer,
                        kafkaValidationResultProducer,
                        session,
                        syfoserviceProducer,
                        oppgaveClient,
                        syfoTilgangsKontrollClient
                )
            }
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

            val validationResult = ValidationResult(status = Status.OK, ruleHits = emptyList())

            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, "")
            coEvery { textMessage.text = any() } returns Unit
            coEvery { session.createTextMessage() } returns textMessage
            coEvery { syfoserviceProducer.send(any()) } returns Unit
            coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()
            coEvery { oppgaveClient.hentOppgave(any(), any()) } returns OpprettOppgaveResponse(123, 1)
            coEvery { oppgaveClient.ferdigStillOppgave(any(), any()) } returns OpprettOppgaveResponse(123, 2)
            coEvery { kafkaApprecProducer.producer.send(any()) } returns mockk<Future<RecordMetadata>>()

            with(handleRequest(HttpMethod.Put, "/api/v1/vurderingmanuelloppgave/$oppgaveid") {
                addHeader("Accept", "application/json")
                addHeader("Content-Type", "application/json")
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                setBody(objectMapper.writeValueAsString(validationResult))
            }) {
                response.status() shouldEqual HttpStatusCode.NoContent
            }
        }
    }
}
