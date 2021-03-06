package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.assertFailsWith
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object HenteManuellOppgaverTest : Spek({

    val database = TestDB()
    val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val authorizationService = AuthorizationService(syfoTilgangsKontrollClient, database)
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)

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

    beforeEachTest {
        clearAllMocks()
        coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, "")
    }

    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("Test av henting av manuelle oppgaver") {
        with(TestApplicationEngine()) {
            start()
            application.routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            it("Skal hente ut manuell oppgave basert på oppgaveid") {
                database.opprettManuellOppgave(manuellOppgave, oppgaveid)
                with(handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/$oppgaveid") {
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    objectMapper.readValue<ManuellOppgaveDTO>(response.content!!).oppgaveid shouldEqual oppgaveid
                }
            }
            it("Skal kaste NumberFormatException når oppgaveid ikke kan parses til int") {
                database.opprettManuellOppgave(manuellOppgave, oppgaveid)
                assertFailsWith<NumberFormatException> {
                    handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/1h2j32k")
                }
            }

            it("Skal returnere notFound når det ikkje finnes noen oppgaver med oppgitt id") {
                with(handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/$oppgaveid") {
                    addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                }) {
                    response.status() shouldEqual HttpStatusCode.NotFound
                }
            }
        }
    }
})
