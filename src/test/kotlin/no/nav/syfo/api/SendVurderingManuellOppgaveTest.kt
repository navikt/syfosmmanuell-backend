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
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
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
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith

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
    val database = TestDB.database
    val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val msGraphClient = mockk<MSGraphClient>()
    val authorizationService = AuthorizationService(syfoTilgangsKontrollClient, msGraphClient, database)
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)

    beforeEachTest {
        clearAllMocks()
    }

    afterEachTest {
        database.connection.dropData()
    }

    describe("Test av api for sending av vurdering") {
        it("Skal returnere InternalServerError når oppdatering av manuelloppgave sitt ValidationResults feilet fordi oppgave ikke finnes") {
            with(TestApplicationEngine()) {
                start()

                database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)

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

                val result = Result(status = ResultStatus.GODKJENT, merknad = null)

                with(
                    handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/21314") {
                        addHeader("Accept", "application/json")
                        addHeader("Content-Type", "application/json")
                        addHeader("X-Nav-Enhet", "1234")
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                        setBody(objectMapper.writeValueAsString(result))
                    }
                ) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                }
            }
        }

        it("should fail when writing sykmelding to kafka fails with status OK") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, msGraphClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                every { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().completeAsync { throw RuntimeException() }
                sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
            }
        }

        it("noContent oppdatering av manuelloppgave med status OK") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, msGraphClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                sendRequest(result, HttpStatusCode.NoContent, oppgaveid)
            }
        }

        it("should fail when X-Nav-Enhet header is empty") {
            with(TestApplicationEngine()) {
                start()
                setUpTest(this, kafkaProducers, syfoTilgangsKontrollClient, msGraphClient, authorizationService, oppgaveService, database, manuellOppgaveService)

                val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                sendRequest(result, HttpStatusCode.BadRequest, oppgaveid, "")
            }
        }
    }

    describe("Merknader") {
        it("Får ikke merknad for status GODKJENT") {
            val result = Result(status = ResultStatus.GODKJENT, merknad = null)
            val merknader = result.toMerknad()

            merknader shouldEqual null
        }

        it("Riktig merknad for status GODKJENT_MED_MERKNAD merknad UGYLDIG_TILBAKEDATERING") {
            val result = Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = MerknadType.UGYLDIG_TILBAKEDATERING)
            val merknad = result.toMerknad()

            merknad shouldEqual Merknad(
                type = "UGYLDIG_TILBAKEDATERING",
                beskrivelse = null
            )
        }

        it("Riktig merknad for status GODKJENT_MED_MERKNAD merknad TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER") {
            val result = Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER)
            val merknad = result.toMerknad()

            merknad shouldEqual Merknad(
                type = "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER",
                beskrivelse = null
            )
        }

        it("Kaster TypeCastException for status GODKJENT_MED_MERKNAD merknad NULL") {
            assertFailsWith<IllegalArgumentException> {
                Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = null).toMerknad()
            }
        }
    }
})

fun TestApplicationEngine.sendRequest(result: Result, statusCode: HttpStatusCode, oppgaveId: Int, navEnhet: String = "1234") {
    with(
        handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/$oppgaveId") {
            addHeader("Accept", "application/json")
            addHeader("Content-Type", "application/json")
            addHeader("X-Nav-Enhet", navEnhet)
            addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
            setBody(objectMapper.writeValueAsString(result))
        }
    ) {
        response.status() shouldEqual statusCode
    }
}

@KtorExperimentalAPI
fun setUpTest(
    testApplicationEngine: TestApplicationEngine,
    kafkaProducers: KafkaProducers,
    syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient,
    msGraphClient: MSGraphClient,
    authorizationService: AuthorizationService,
    oppgaveService: OppgaveService,
    database: DatabaseInterface,
    manuellOppgaveService: ManuellOppgaveService
) {
    val sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic"
    val sm2013ApprecTopicName = "sm2013ApprecTopicName"

    coEvery { kafkaProducers.kafkaApprecProducer.producer } returns mockk()
    coEvery { kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic } returns sm2013ApprecTopicName

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer } returns mockk()
    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic } returns sm2013AutomaticHandlingTopic
    coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, "")
    coEvery { msGraphClient.getSubjectFromMsGraph(any()) } returns "4321"

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    coEvery { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) } returns Unit
    coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)

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
