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
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CompletableFuture
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
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
import org.junit.jupiter.api.BeforeEach

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

    val kafkaApprecProducer = mockk<KafkaProducers.KafkaApprecProducer>()
    val kafkaRecievedSykmeldingProducer = mockk<KafkaProducers.KafkaRecievedSykmeldingProducer>()
    val kafkaValidationResultProducer = mockk<KafkaProducers.KafkaValidationResultProducer>()

    val sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic"
    val sm2013InvalidHandlingTopic = "sm2013InvalidHandlingTopic"
    val sm2013BehandlingsUtfallTopic = "sm2013BehandlingsUtfallTopic"
    val sm2013ApprecTopicName = "sm2013ApprecTopicName"

    val textMessage = mockk<TextMessage>()

    val syfoserviceQueueName = "syfoserviceQueueName"
    private val session = mockk<Session>()
    private val syfoserviceProducer = mockk<MessageProducer>()
    @KtorExperimentalAPI
    private val oppgaveService = mockk<OppgaveService>()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
    }

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
                        oppgaveService,
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
    internal fun `should fail when writing sykmelding to kafka fails with status INVALID`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)

            val validationResult = ValidationResult(status = Status.INVALID, ruleHits = emptyList())
            every { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
            sendRequest(validationResult, HttpStatusCode.InternalServerError, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `should fail when writing sykmelding to kafka fails with status OK`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)

            val validationResult = ValidationResult(status = Status.OK, ruleHits = emptyList())
            every { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
            sendRequest(validationResult, HttpStatusCode.InternalServerError, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `should fail when writing appreck OK to kafka`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)
            val validationResult = ValidationResult(status = Status.OK, ruleHits = emptyList())
            every { kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
            val statusCode = HttpStatusCode.InternalServerError
            sendRequest(validationResult, statusCode, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `should fail when writing appreck INVALID to kafka`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)
            val validationResult = ValidationResult(status = Status.INVALID, ruleHits = emptyList())
            every { kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
            val statusCode = HttpStatusCode.InternalServerError
            sendRequest(validationResult, statusCode, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `should fail when writing validation result INVALID to kafka`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)
            val validationResult = ValidationResult(status = Status.INVALID, ruleHits = emptyList())
            every { kafkaValidationResultProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
            val statusCode = HttpStatusCode.InternalServerError
            sendRequest(validationResult, statusCode, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `noConten oppdatering av manuelloppgave med status OK`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)
            val validationResult = ValidationResult(status = Status.OK, ruleHits = emptyList())
            sendRequest(validationResult, HttpStatusCode.NoContent, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    @Test
    internal fun `noConten oppdatering av manuelloppgave med status INVALID`() {
        with(TestApplicationEngine()) {
            start()
            setUpTest(this)
            val validationResult = ValidationResult(status = Status.INVALID, ruleHits = emptyList())
            sendRequest(validationResult, HttpStatusCode.NoContent, oppgaveid)
        }
    }

    @KtorExperimentalAPI
    private fun setUpTest(testApplicationEngine: TestApplicationEngine) {
        coEvery { kafkaApprecProducer.producer } returns mockk()
        coEvery { kafkaApprecProducer.sm2013ApprecTopic } returns sm2013ApprecTopicName

        coEvery { kafkaValidationResultProducer.producer } returns mockk()
        coEvery { kafkaValidationResultProducer.syfoserviceQueueName } returns syfoserviceQueueName

        coEvery { kafkaRecievedSykmeldingProducer.producer } returns mockk()
        coEvery { kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns sm2013AutomaticHandlingTopic
        coEvery { kafkaRecievedSykmeldingProducer.sm2013InvalidHandlingTopic } returns sm2013InvalidHandlingTopic
        coEvery { kafkaRecievedSykmeldingProducer.sm2013BehandlingsUtfallTopic } returns sm2013BehandlingsUtfallTopic
        coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, "")
        coEvery { textMessage.text = any() } returns Unit
        coEvery { session.createTextMessage() } returns textMessage
        coEvery { syfoserviceProducer.send(any()) } returns Unit
        coEvery { kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        coEvery { oppgaveService.ferdigstillOppgave(any(), any()) } returns Unit
        coEvery { kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        coEvery { kafkaValidationResultProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        database.opprettManuellOppgave(manuellOppgave, oppgaveid)

        testApplicationEngine.application.routing {
            sendVurderingManuellOppgave(
                    manuellOppgaveService,
                    kafkaApprecProducer,
                    kafkaRecievedSykmeldingProducer,
                    kafkaValidationResultProducer,
                    session,
                    syfoserviceProducer,
                    oppgaveService,
                    syfoTilgangsKontrollClient
            )
        }
        testApplicationEngine.application.install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
        testApplicationEngine.application.install(StatusPages) {
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception", cause)
            }
        }
    }
}

private fun TestApplicationEngine.sendRequest(validationResult: ValidationResult, statusCode: HttpStatusCode, oppgaveId: Int) {
    with(handleRequest(HttpMethod.Put, "/api/v1/vurderingmanuelloppgave/$oppgaveId") {
        addHeader("Accept", "application/json")
        addHeader("Content-Type", "application/json")
        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
        setBody(objectMapper.writeValueAsString(validationResult))
    }) {
        response.status() shouldEqual statusCode
    }
}
