package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveStatus
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
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.assertEquals

const val oppgaveid = 308076319
const val manuelloppgaveId = "1314"

val manuellOppgave =
    ManuellOppgave(
        receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
        validationResult = ValidationResult(Status.OK, emptyList()),
        apprec =
            objectMapper.readValue(
                Apprec::class
                    .java
                    .getResourceAsStream("/apprecOK.json")!!
                    .readBytes()
                    .toString(
                        Charsets.UTF_8,
                    ),
            ),
    )

class SendVurderingManuellOppgaveTest :
    FunSpec({
        val database = TestDB.database
        val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
        val msGraphClient = mockk<MSGraphClient>()
        val authorizationService =
            AuthorizationService(istilgangskontrollClient, msGraphClient, database)
        val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
        val oppgaveService = mockk<OppgaveService>(relaxed = true)
        val manuellOppgaveService =
            ManuellOppgaveService(
                database,
                istilgangskontrollClient,
                kafkaProducers,
                oppgaveService
            )

        beforeTest {
            clearMocks(istilgangskontrollClient, msGraphClient, kafkaProducers, oppgaveService)
        }

        afterTest { database.connection.dropData() }

        context("Test av api for sending av vurdering") {
            test(
                "Skal returnere InternalServerError når oppdatering av manuelloppgave sitt ValidationResults feilet fordi oppgave ikke finnes"
            ) {
                with(TestApplicationEngine()) {
                    start()

                    database.opprettManuellOppgave(
                        manuellOppgave,
                        manuellOppgave.apprec,
                        oppgaveid,
                        ManuellOppgaveStatus.APEN,
                        LocalDateTime.now(),
                    )

                    application.routing {
                        sendVurderingManuellOppgave(
                            manuellOppgaveService,
                            authorizationService,
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
                        exception<Throwable> { call, cause ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                cause.message ?: "Unknown error"
                            )
                            log.error("Caught exception", cause)
                        }
                    }

                    val result = Result(status = ResultStatus.GODKJENT, merknad = null)

                    with(
                        handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/21314") {
                            addHeader("Accept", "application/json")
                            addHeader("Content-Type", "application/json")
                            addHeader("X-Nav-Enhet", "1234")
                            addHeader(
                                HttpHeaders.Authorization,
                                "Bearer ${generateJWT(
                                "2",
                                "clientId",
                                Claim("preferred_username", "firstname.lastname@nav.no"),
                            )}",
                            )
                            setBody(objectMapper.writeValueAsString(result))
                        },
                    ) {
                        assertEquals(HttpStatusCode.NotFound, response.status())
                    }
                }
            }

            test("should fail when writing sykmelding to kafka fails with status OK") {
                with(TestApplicationEngine()) {
                    start()
                    setUpTest(
                        this,
                        kafkaProducers,
                        istilgangskontrollClient,
                        msGraphClient,
                        authorizationService,
                        oppgaveService,
                        database,
                        manuellOppgaveService
                    )

                    val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                    every {
                        kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any())
                    } returns
                        CompletableFuture<RecordMetadata>().completeAsync {
                            throw RuntimeException()
                        }
                    sendRequest(result, HttpStatusCode.InternalServerError, oppgaveid)
                }
            }

            test("noContent oppdatering av manuelloppgave med status OK") {
                with(TestApplicationEngine()) {
                    start()
                    setUpTest(
                        this,
                        kafkaProducers,
                        istilgangskontrollClient,
                        msGraphClient,
                        authorizationService,
                        oppgaveService,
                        database,
                        manuellOppgaveService
                    )

                    val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                    sendRequest(result, HttpStatusCode.NoContent, oppgaveid)
                }
            }

            test("should fail when X-Nav-Enhet header is empty") {
                with(TestApplicationEngine()) {
                    start()
                    setUpTest(
                        this,
                        kafkaProducers,
                        istilgangskontrollClient,
                        msGraphClient,
                        authorizationService,
                        oppgaveService,
                        database,
                        manuellOppgaveService
                    )

                    val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                    sendRequest(result, HttpStatusCode.BadRequest, oppgaveid, "")
                }
            }
        }

        context("Merknader") {
            test("Får ikke merknad for status GODKJENT") {
                val result = Result(status = ResultStatus.GODKJENT, merknad = null)
                val merknader = result.toMerknad()

                assertEquals(null, merknader)
            }

            test("Riktig merknad for status GODKJENT_MED_MERKNAD merknad UGYLDIG_TILBAKEDATERING") {
                val result =
                    Result(
                        status = ResultStatus.GODKJENT_MED_MERKNAD,
                        merknad = MerknadType.UGYLDIG_TILBAKEDATERING
                    )
                val merknad = result.toMerknad()

                assertEquals(
                    Merknad(
                        type = "UGYLDIG_TILBAKEDATERING",
                        beskrivelse = null,
                    ),
                    merknad,
                )
            }

            test(
                "Riktig merknad for status GODKJENT_MED_MERKNAD merknad TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER"
            ) {
                val result =
                    Result(
                        status = ResultStatus.GODKJENT_MED_MERKNAD,
                        merknad = MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER,
                    )
                val merknad = result.toMerknad()

                assertEquals(
                    Merknad(
                        type = "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER",
                        beskrivelse = null,
                    ),
                    merknad,
                )
            }

            test("Kaster TypeCastException for status GODKJENT_MED_MERKNAD merknad NULL") {
                assertFailsWith<IllegalArgumentException> {
                    Result(status = ResultStatus.GODKJENT_MED_MERKNAD, merknad = null).toMerknad()
                }
            }
        }
    })

fun TestApplicationEngine.sendRequest(
    result: Result,
    statusCode: HttpStatusCode,
    oppgaveId: Int,
    navEnhet: String = "1234"
) {
    with(
        handleRequest(HttpMethod.Post, "/api/v1/vurderingmanuelloppgave/$oppgaveId") {
            addHeader("Accept", "application/json")
            addHeader("Content-Type", "application/json")
            addHeader("X-Nav-Enhet", navEnhet)
            addHeader(
                HttpHeaders.Authorization,
                "Bearer ${generateJWT(
                    "2",
                    "clientId",
                    Claim("preferred_username", "firstname.lastname@nav.no"),
                )}",
            )
            setBody(objectMapper.writeValueAsString(result))
        },
    ) {
        assertEquals(statusCode, response.status())
    }
}

fun setUpTest(
    testApplicationEngine: TestApplicationEngine,
    kafkaProducers: KafkaProducers,
    istilgangskontrollClient: IstilgangskontrollClient,
    msGraphClient: MSGraphClient,
    authorizationService: AuthorizationService,
    oppgaveService: OppgaveService,
    database: DatabaseInterface,
    manuellOppgaveService: ManuellOppgaveService,
) {
    val sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic"
    val sm2013ApprecTopicName = "sm2013ApprecTopicName"

    coEvery { kafkaProducers.kafkaApprecProducer.producer } returns mockk()
    coEvery { kafkaProducers.kafkaApprecProducer.apprecTopic } returns sm2013ApprecTopicName

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer } returns mockk()
    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.okSykmeldingTopic } returns
        sm2013AutomaticHandlingTopic
    coEvery {
        istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any())
    } returns Tilgang(true)
    coEvery { msGraphClient.getSubjectFromMsGraph(any()) } returns "4321"

    coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns
        CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    coEvery { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) } returns Unit
    coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns
        CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    runBlocking {
        database.opprettManuellOppgave(
            manuellOppgave,
            manuellOppgave.apprec,
            oppgaveid,
            ManuellOppgaveStatus.APEN,
            LocalDateTime.now(),
        )
    }

    testApplicationEngine.application.routing {
        sendVurderingManuellOppgave(
            manuellOppgaveService,
            authorizationService,
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
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
            log.error("Caught exception", cause)
        }
    }
}
