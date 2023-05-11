package no.nav.syfo.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
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
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith

class HenteManuellOppgaverTest : FunSpec({

    val applicationState = ApplicationState(alive = true, ready = true)
    val database = TestDB.database
    val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val msGraphClient = mockk<MSGraphClient>()
    val authorizationService = AuthorizationService(syfoTilgangsKontrollClient, msGraphClient, database)
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveService =
        ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)

    val manuelloppgaveId = "1314"
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
        validationResult = ValidationResult(Status.OK, emptyList()),
        apprec = objectMapper.readValue(
            Apprec::class.java.getResourceAsStream("/apprecOK.json")!!.readBytes().toString(
                Charsets.UTF_8,
            ),
        ),
    )
    val oppgaveid = 308076319

    beforeTest {
        clearMocks(syfoTilgangsKontrollClient, msGraphClient, kafkaProducers, oppgaveService)
        coEvery {
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                any(),
                any(),
            )
        } returns Tilgang(true)
    }

    afterTest {
        database.connection.dropData()
    }

    context("Test av henting av manuelle oppgaver") {

        test("Skal hente ut manuell oppgave basert på oppgaveid") {
            with(TestApplicationEngine()) {
                start()
                application.routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                application.install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                application.install(StatusPages) {
                    exception<NumberFormatException> { call, cause ->
                        call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                        log.error("Caught exception", cause)
                        throw cause
                    }
                    exception<IkkeTilgangException> { call, cause ->
                        call.respond(HttpStatusCode.Forbidden)
                        log.error("Caught exception", cause)
                        throw cause
                    }
                    exception<Throwable> { call, cause ->
                        call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                        log.error("Caught exception", cause)
                        if (cause is ExecutionException) {
                            log.error("Exception is ExecutionException, restarting..")
                            applicationState.ready = false
                            applicationState.alive = false
                        }
                        throw cause
                    }
                }
                database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/$oppgaveid") {
                        addHeader(
                            HttpHeaders.Authorization,
                            "Bearer ${generateJWT(
                                "2",
                                "clientId",
                                Claim("preferred_username", "firstname.lastname@nav.no"),
                            )}",
                        )
                    },
                ) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    assertEquals(oppgaveid, objectMapper.readValue<ManuellOppgaveDTO>(response.content!!).oppgaveid)
                }
            }
        }
        test("Skal kaste NumberFormatException når oppgaveid ikke kan parses til int") {
            with(TestApplicationEngine()) {
                start()
                application.routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                application.install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                application.install(StatusPages) {
                    exception<NumberFormatException> { call, cause ->
                        call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                        log.error("Caught exception", cause)
                        throw cause
                    }
                    exception<IkkeTilgangException> { call, cause ->
                        call.respond(HttpStatusCode.Forbidden)
                        log.error("Caught exception", cause)
                        throw cause
                    }
                    exception<Throwable> { call, cause ->
                        call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                        log.error("Caught exception", cause)
                        if (cause is ExecutionException) {
                            log.error("Exception is ExecutionException, restarting..")
                            applicationState.ready = false
                            applicationState.alive = false
                        }
                        throw cause
                    }
                }
                database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)
                assertFailsWith<NumberFormatException> {
                    handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/1h2j32k")
                }
            }
        }

        test("Skal returnere notFound når det ikkje finnes noen oppgaver med oppgitt id") {
            with(TestApplicationEngine()) {
                start()
                application.routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                application.install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
                application.install(StatusPages) {
                    exception<NumberFormatException> { call, cause ->
                        call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                        log.error("Caught exception", cause)
                        throw cause
                    }
                    exception<IkkeTilgangException> { call, cause ->
                        call.respond(HttpStatusCode.Forbidden)
                        log.error("Caught exception", cause)
                        throw cause
                    }
                    exception<Throwable> { call, cause ->
                        call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                        log.error("Caught exception", cause)
                        if (cause is ExecutionException) {
                            log.error("Exception is ExecutionException, restarting..")
                            applicationState.ready = false
                            applicationState.alive = false
                        }
                        throw cause
                    }
                }
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/$oppgaveid") {
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    },
                ) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }
    }
})
