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
import io.mockk.verify
import java.util.concurrent.CompletableFuture
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.client.Veileder
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.api.Result
import no.nav.syfo.persistering.api.RuleInfoTekst
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.persistering.api.tilValidationResult
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.AuthorizationService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

const val oppgaveid = 308076319
const val manuelloppgaveId = "1314"

val manuellOppgave = ManuellOppgave(
    receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
    validationResult = ValidationResult(Status.OK, emptyList()),
    apprec = objectMapper.readValue(
        Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
            Charsets.UTF_8
        )
    )
)

@KtorExperimentalAPI
object SendVurderingManuellOppgaveTest : Spek({
    val database = TestDB()
    val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val authorizationService = AuthorizationService(syfoTilgangsKontrollClient)
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveService = ManuellOppgaveService(database, authorizationService, kafkaProducers, oppgaveService)

    beforeEachTest {
        clearAllMocks()
    }

    afterEachTest {
        database.connection.dropData()
    }

    afterGroup {
        database.stop()
    }

    describe("Test av api for sending av vurdering") {
        it("Skal returnere InternalServerError når oppdatering av manuelloppgave sitt ValidationResults feilet fordi oppgave ikke finnes") {
            with(TestApplicationEngine()) {
                start()

                coEvery { kafkaProducers.kafkaValidationResultProducer.producer } returns mockk<KafkaProducer<String, ValidationResult>>()

                database.opprettManuellOppgave(manuellOppgave, oppgaveid)

                application.routing {
                    sendVurderingManuellOppgave(
                        manuellOppgaveService
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
                    }
                }

                val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_MANGLER_BEGRUNNELSE")

                with(handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/21314") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    setBody(objectMapper.writeValueAsString(result))
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                    response.content shouldEqual "Fant ikke oppgave med id 21314"
                }
            }
        }

        it("should fail when writing sykmelding to kafka fails with status INVALID") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)

                val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_MANGLER_BEGRUNNELSE")
                every { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("should fail when writing sykmelding to kafka fails with status OK") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)

                val result = Result(godkjent = true, avvisningstekst = null)
                every { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("should fail when writing appreck OK to kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                val result = Result(godkjent = true, avvisningstekst = null)
                every { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val statusCode = HttpStatusCode.InternalServerError
                sendRequest(result, statusCode, oppgaveid)
            }
        }

        it("should fail when writing apprec INVALID to kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_MANGLER_BEGRUNNELSE")
                every { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val statusCode = HttpStatusCode.InternalServerError
                sendRequest(result, statusCode, oppgaveid)
            }
        }

        it("should fail when writing validation result INVALID to kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_MANGLER_BEGRUNNELSE")
                every { kafkaProducers.kafkaValidationResultProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val statusCode = HttpStatusCode.InternalServerError
                sendRequest(result, statusCode, oppgaveid)
            }
        }

        it("noContent oppdatering av manuelloppgave med status OK") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                val result = Result(godkjent = true, avvisningstekst = null)
                sendRequest(result, HttpStatusCode.NoContent, oppgaveid)
            }
        }

        it("noConten oppdatering av manuelloppgave med status INVALID") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_MANGLER_BEGRUNNELSE")
                sendRequest(result, HttpStatusCode.NoContent, oppgaveid)
                verify(exactly = 0) { kafkaProducers.kafkaSyfoserviceProducer.producer.send(any()) }
            }
        }

        it("should fail when writing sykmelding syfoservice kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                every { kafkaProducers.kafkaSyfoserviceProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val result = Result(godkjent = true, avvisningstekst = null)
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("should fail when X-Nav-Enhet header is empty") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, oppgaveService, database, manuellOppgaveService)
                val result = Result(godkjent = true, avvisningstekst = null)
                sendRequest(result, HttpStatusCode.BadRequest, oppgaveid, "")
            }
        }
    }

    describe("Får riktig ValidationResult") {
        it("Riktig ValidationResult for godkjent sykmelding") {
            val result = Result(godkjent = true, avvisningstekst = null)

            val validationResult = result.tilValidationResult()

            validationResult.status shouldEqual Status.OK
            validationResult.ruleHits shouldEqual emptyList()
        }
        it("Riktig ValidationResult for sykmelding som er avvist pga manglende begrunnelse") {
            val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_MANGLER_BEGRUNNELSE")

            val validationResult = result.tilValidationResult()

            validationResult.status shouldEqual Status.INVALID
            validationResult.ruleHits.size shouldEqual 1
            validationResult.ruleHits.first() shouldEqual RuleInfo(
                ruleName = RuleInfoTekst.TILBAKEDATERT_MANGLER_BEGRUNNELSE.name,
                messageForUser = RuleInfoTekst.TILBAKEDATERT_MANGLER_BEGRUNNELSE.messageForUser,
                messageForSender = RuleInfoTekst.TILBAKEDATERT_MANGLER_BEGRUNNELSE.messageForSender,
                ruleStatus = Status.INVALID
            )
        }
        it("Riktig ValidationResult for sykmelding som er avvist pga begrunnelse ikke godtatt") {
            val result = Result(godkjent = false, avvisningstekst = "TILBAKEDATERT_IKKE_GODTATT")

            val validationResult = result.tilValidationResult()

            validationResult.status shouldEqual Status.INVALID
            validationResult.ruleHits.size shouldEqual 1
            validationResult.ruleHits.first() shouldEqual RuleInfo(
                ruleName = RuleInfoTekst.TILBAKEDATERT_IKKE_GODTATT.name,
                messageForUser = RuleInfoTekst.TILBAKEDATERT_IKKE_GODTATT.messageForUser,
                messageForSender = RuleInfoTekst.TILBAKEDATERT_IKKE_GODTATT.messageForSender,
                ruleStatus = Status.INVALID
            )
        }
    }
})

fun TestApplicationEngine.sendRequest(result: Result, statusCode: HttpStatusCode, oppgaveId: Int, navEnhet: String = "1234") {
    with(handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/$oppgaveId") {
        addHeader("Accept", "application/json")
        addHeader("Content-Type", "application/json")
        addHeader("X-Nav-Enhet", navEnhet)
        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
        setBody(objectMapper.writeValueAsString(result))
    }) {
        response.status() shouldEqual statusCode
    }
}

@KtorExperimentalAPI
fun setUpTest(testApplicationEngine: TestApplicationEngine, kafkaProducers: KafkaProducers, syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient, oppgaveService: OppgaveService, database: DatabaseInterface, manuellOppgaveService: ManuellOppgaveService) {
    val sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic"
    val sm2013InvalidHandlingTopic = "sm2013InvalidHandlingTopic"
    val sm2013BehandlingsUtfallTopic = "sm2013BehandlingsUtfallTopic"
    val sm2013ApprecTopicName = "sm2013ApprecTopicName"

    coEvery { kafkaProducers.kafkaApprecProducer.producer } returns mockk()
    coEvery { kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic } returns sm2013ApprecTopicName

    coEvery { kafkaProducers.kafkaValidationResultProducer.producer } returns mockk()

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer } returns mockk()
    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns sm2013AutomaticHandlingTopic
    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.sm2013InvalidHandlingTopic } returns sm2013InvalidHandlingTopic
    coEvery { kafkaProducers.kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic } returns sm2013BehandlingsUtfallTopic
    coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, "")
    coEvery { syfoTilgangsKontrollClient.hentVeilderIdentViaAzure(any()) } returns Veileder(veilederIdent = "4321")

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    coEvery { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) } returns Unit
    coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    coEvery { kafkaProducers.kafkaValidationResultProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    database.opprettManuellOppgave(manuellOppgave, oppgaveid)

    testApplicationEngine.application.routing {
        sendVurderingManuellOppgave(
            manuellOppgaveService
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
