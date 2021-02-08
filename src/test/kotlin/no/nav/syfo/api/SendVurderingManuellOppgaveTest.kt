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
import kotlin.test.assertFailsWith
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.client.Veileder
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.api.AvvisningType
import no.nav.syfo.persistering.api.MerknadType
import no.nav.syfo.persistering.api.Result
import no.nav.syfo.persistering.api.ResultStatus
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
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
    val authorizationService = AuthorizationService(syfoTilgangsKontrollClient, database)
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)

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
                        manuellOppgaveService,
                        authorizationService
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

                val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)

                with(handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/21314") {
                    addHeader("Accept", "application/json")
                    addHeader("Content-Type", "application/json")
                    addHeader("X-Nav-Enhet", "1234")
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    setBody(objectMapper.writeValueAsString(result))
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                }
            }
        }

        it("should fail when writing sykmelding to kafka fails with status INVALID") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = AvvisningType.MANGLER_BEGRUNNELSE)
                every { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("should fail when writing sykmelding to kafka fails with status OK") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)
                every { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("should fail when writing appreck OK to kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)
                every { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val statusCode = HttpStatusCode.InternalServerError
                sendRequest(result, statusCode, oppgaveid)
            }
        }

        it("should fail when writing apprec INVALID to kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = AvvisningType.MANGLER_BEGRUNNELSE)
                every { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val statusCode = HttpStatusCode.InternalServerError
                sendRequest(result, statusCode, oppgaveid)
            }
        }

        it("should fail when writing validation result INVALID to kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = AvvisningType.MANGLER_BEGRUNNELSE)
                every { kafkaProducers.kafkaValidationResultProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                val statusCode = HttpStatusCode.InternalServerError
                sendRequest(result, statusCode, oppgaveid)
            }
        }

        it("noContent oppdatering av manuelloppgave med status OK") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)
                sendRequest(result, HttpStatusCode.NoContent, oppgaveid)
            }
        }

        it("noConten oppdatering av manuelloppgave med status INVALID") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = AvvisningType.MANGLER_BEGRUNNELSE)
                sendRequest(result, HttpStatusCode.NoContent, oppgaveid)
                verify(exactly = 0) { kafkaProducers.kafkaSyfoserviceProducer.producer.send(any()) }
            }
        }

        it("should fail when writing sykmelding syfoservice kafka") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)
                every { kafkaProducers.kafkaSyfoserviceProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }

                val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("should fail when X-Nav-Enhet header is empty") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)
                sendRequest(result, HttpStatusCode.BadRequest, oppgaveid, "")
            }
        }
    }

    describe("ValidationResult") {
        it("Riktig ValidationResult for status GODKJENT") {
            val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)

            val validationResult = result.toValidationResult()

            validationResult.status shouldEqual Status.OK
            validationResult.ruleHits shouldEqual emptyList()
        }

        it("Riktig ValidationResult for status GODKJENT_MED_MERKNAD") {
            val result = Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = null, avvisningType = null)

            val validationResult = result.toValidationResult()

            validationResult.status shouldEqual Status.OK
            validationResult.ruleHits shouldEqual emptyList()
        }

        it("Riktig ValidationResult for status AVVIST avvisningtype MANGLER_BEGRUNNELSE") {
            val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = AvvisningType.MANGLER_BEGRUNNELSE)

            val validationResult = result.toValidationResult()

            validationResult.status shouldEqual Status.INVALID
            validationResult.ruleHits.size shouldEqual 1
            validationResult.ruleHits.first() shouldEqual RuleInfo(
                ruleName = "TILBAKEDATERT_MANGLER_BEGRUNNELSE",
                messageForSender = "Sykmelding gjelder som hovedregel fra den dagen pasienten oppsøker behandler. Sykmeldingen er tilbakedatert uten at det kommer tydelig nok fram hvorfor dette var nødvendig. Sykmeldingen er derfor avvist, og det må skrives en ny hvis det fortsatt er aktuelt med sykmelding. Pasienten har fått beskjed om å vente på ny sykmelding fra deg.",
                messageForUser = "Sykmelding gjelder som hovedregel fra den dagen du oppsøker behandler. Sykmeldingen din er tilbakedatert uten at det er gitt en god nok begrunnelse for dette. Behandleren din må skrive ut en ny sykmelding og begrunne bedre hvorfor den er tilbakedatert. Din behandler har mottatt melding fra NAV om dette.",
                ruleStatus = Status.INVALID
            )
        }

        it("Riktig ValidationResult for status AVVIST avvisningtype UGYLDIG_BEGRUNNELSE") {
            val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = AvvisningType.UGYLDIG_BEGRUNNELSE)

            val validationResult = result.toValidationResult()

            validationResult.status shouldEqual Status.INVALID
            validationResult.ruleHits.size shouldEqual 1
            validationResult.ruleHits.first() shouldEqual RuleInfo(
                ruleName = "UGYLDIG_BEGRUNNELSE",
                messageForSender = "NAV kan ikke godta tilbakedateringen. Sykmeldingen er derfor avvist. Hvis sykmelding fortsatt er aktuelt, må det skrives ny sykmelding der f.o.m.-dato er dagen du var i kontakt med pasienten. Pasienten har fått beskjed om å vente på ny sykmelding fra deg.",
                messageForUser = "NAV kan ikke godta sykmeldingen din fordi den starter før dagen du tok kontakt med behandleren. Trenger du fortsatt sykmelding, må behandleren din skrive en ny som gjelder fra den dagen dere var i kontakt. Behandleren din har fått beskjed fra NAV om dette.",
                ruleStatus = Status.INVALID
            )
        }

        it("Kaster TypeCastException for status AVVIST avvisningtype NULL") {
            assertFailsWith<IllegalArgumentException> {
                Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = null).toValidationResult()
            }
        }
    }

    describe("Merknader") {
        it("Får ikke merknad for status GODKJENT") {
            val result = Result(status = ResultStatus.GODKJENT, merknad = null, avvisningType = null)
            val merknader = result.toMerknad()

            merknader shouldEqual null
        }

        it("Riktig merknad for status GODKJENT_MED_MERKNAD merknad UGYLDIG_TILBAKEDATERING") {
            val result = Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = MerknadType.UGYLDIG_TILBAKEDATERING, avvisningType = null)
            val merknad = result.toMerknad()

            merknad shouldEqual Merknad(
                type = "UGYLDIG_TILBAKEDATERING",
                beskrivelse = null
            )
        }

        it("Riktig merknad for status GODKJENT_MED_MERKNAD merknad TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER") {
            val result = Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER, avvisningType = null)
            val merknad = result.toMerknad()

            merknad shouldEqual Merknad(
                type = "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER",
                beskrivelse = null
            )
        }

        it("Får ikke merknad for status AVVIST") {
            val result = Result(status = ResultStatus.AVVIST, merknad = null, avvisningType = null)
            val merknad = result.toMerknad()

            merknad shouldEqual null
        }

        it("Kaster TypeCastException for status GODKJENT_MED_MERKNAD merknad NULL") {
            assertFailsWith<IllegalArgumentException> {
                Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = null, avvisningType = null).toMerknad()
            }
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
fun setUpTest(
    testApplicationEngine: TestApplicationEngine,
    kafkaProducers: KafkaProducers,
    syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient,
    authorizationService: AuthorizationService,
    oppgaveService: OppgaveService,
    database: DatabaseInterface,
    manuellOppgaveService: ManuellOppgaveService
) {
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
    coEvery { syfoTilgangsKontrollClient.hentVeilederIdentViaAzure(any()) } returns Veileder("4321")

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    coEvery { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) } returns Unit
    coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    coEvery { kafkaProducers.kafkaValidationResultProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    database.opprettManuellOppgave(manuellOppgave, oppgaveid)

    testApplicationEngine.application.routing {
        sendVurderingManuellOppgave(
            manuellOppgaveService,
            authorizationService
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
