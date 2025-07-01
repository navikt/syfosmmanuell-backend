package no.nav.syfo.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ExecutionException
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.logger
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.IkkeTilgangException
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.junit.jupiter.api.Assertions.assertEquals

class HenteManuellOppgaverTest :
    FunSpec({
        val applicationState = ApplicationState(alive = true, ready = true)
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
                oppgaveService,
                "app",
                "namespace"
            )

        val manuelloppgaveId = "1314"
        val manuellOppgave =
            ManuellOppgave(
                receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
                validationResult =
                    ValidationResult(Status.OK, emptyList(), OffsetDateTime.now(ZoneOffset.UTC)),
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
        val oppgaveid = 308076319

        beforeTest {
            clearMocks(istilgangskontrollClient, msGraphClient, kafkaProducers, oppgaveService)
            coEvery {
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    any(),
                    any(),
                )
            } returns Tilgang(true)
        }

        afterTest { database.connection.dropData() }

        context("Test av henting av manuelle oppgaver") {
            test("Skal hente ut manuell oppgave basert på oppgaveid") {
                testApplication {
                    application {
                        routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            }
                        }
                        install(StatusPages) {
                            exception<NumberFormatException> { call, cause ->
                                call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                                logger.error("Caught exception", cause)
                                throw cause
                            }
                            exception<IkkeTilgangException> { call, cause ->
                                call.respond(HttpStatusCode.Forbidden)
                                logger.error("Caught exception", cause)
                                throw cause
                            }
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    cause.message ?: "Unknown error"
                                )
                                logger.error("Caught exception", cause)
                                if (cause is ExecutionException) {
                                    logger.error("Exception is ExecutionException, restarting..")
                                    applicationState.ready = false
                                    applicationState.alive = false
                                }
                                throw cause
                            }
                        }
                    }
                    database.opprettManuellOppgave(
                        manuellOppgave,
                        manuellOppgave.apprec,
                        oppgaveid,
                        ManuellOppgaveStatus.APEN,
                        LocalDateTime.now(),
                    )
                    val response =
                        client.get("/api/v1/manuellOppgave/$oppgaveid") {
                            headers {
                                append(
                                    HttpHeaders.Authorization,
                                    "Bearer ${
                                    generateJWT(
                                        "2",
                                        "clientId",
                                        Claim("preferred_username", "firstname.lastname@nav.no"),
                                    )
                                }",
                                )
                            }
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        oppgaveid,
                        objectMapper.readValue<ManuellOppgaveDTO>(response.bodyAsText()).oppgaveid
                    )
                }
            }
        }
        test("Skal kaste NumberFormatException når oppgaveid ikke kan parses til int") {
            testApplication {
                application {
                    routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                    install(ContentNegotiation) {
                        jackson {
                            registerKotlinModule()
                            registerModule(JavaTimeModule())
                            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        }
                    }
                    install(StatusPages) {
                        exception<NumberFormatException> { call, cause ->
                            call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                            logger.error("Caught exception", cause)
                            throw cause
                        }
                        exception<IkkeTilgangException> { call, cause ->
                            call.respond(HttpStatusCode.Forbidden)
                            logger.error("Caught exception", cause)
                            throw cause
                        }
                        exception<Throwable> { call, cause ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                cause.message ?: "Unknown error"
                            )
                            logger.error("Caught exception", cause)
                            if (cause is ExecutionException) {
                                logger.error("Exception is ExecutionException, restarting..")
                                applicationState.ready = false
                                applicationState.alive = false
                            }
                            throw cause
                        }
                    }
                }
                database.opprettManuellOppgave(
                    manuellOppgave,
                    manuellOppgave.apprec,
                    oppgaveid,
                    ManuellOppgaveStatus.APEN,
                    LocalDateTime.now(),
                )

                val response = client.get("/api/v1/manuellOppgave/1h2j32k")

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("oppgaveid is not a number", response.bodyAsText())
            }
        }

        test("Skal returnere notFound når det ikkje finnes noen oppgaver med oppgitt id") {
            testApplication {
                application {
                    routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                    install(ContentNegotiation) {
                        jackson {
                            registerKotlinModule()
                            registerModule(JavaTimeModule())
                            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        }
                    }
                    install(StatusPages) {
                        exception<NumberFormatException> { call, cause ->
                            call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                            logger.error("Caught exception", cause)
                            throw cause
                        }
                        exception<IkkeTilgangException> { call, cause ->
                            call.respond(HttpStatusCode.Forbidden)
                            logger.error("Caught exception", cause)
                            throw cause
                        }
                        exception<Throwable> { call, cause ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                cause.message ?: "Unknown error"
                            )
                            logger.error("Caught exception", cause)
                            if (cause is ExecutionException) {
                                logger.error("Exception is ExecutionException, restarting..")
                                applicationState.ready = false
                                applicationState.alive = false
                            }
                            throw cause
                        }
                    }
                }

                val response =
                    client.get("/api/v1/manuellOppgave/$oppgaveid") {
                        headers {
                            append(
                                HttpHeaders.Authorization,
                                "Bearer ${generateJWT("2", "clientId")}"
                            )
                        }
                    }

                assertEquals(HttpStatusCode.NotFound, response.status)
            }
        }
    })
