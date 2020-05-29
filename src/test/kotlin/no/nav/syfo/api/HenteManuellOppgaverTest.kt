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
import io.mockk.coEvery
import io.mockk.mockk
import javax.jms.MessageProducer
import javax.jms.Session
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.api.hentManuellOppgaver
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
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class HenteManuellOppgaverTest {

    private val database = TestDB()
    private val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    private val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    private val oppgaveService = mockk<OppgaveService>(relaxed = true)
    private val syfoserviceProducer = mockk<MessageProducer>(relaxed = true)
    private val session = mockk<Session>(relaxed = true)
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService, syfoserviceProducer, session, "")

    private val manuelloppgaveId = "1314"

    private val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
        validationResult = ValidationResult(Status.OK, emptyList()),
        apprec = objectMapper.readValue(
            Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
                Charsets.UTF_8
            )
        )
    )
    private val oppgaveid = 308076319

    @Test
    internal fun `Skal hente ut manuell oppgaver basert, på oppgaveid`() {
        with(TestApplicationEngine()) {
            start()
            coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(
                true,
                ""
            )
            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            application.routing { hentManuellOppgaver(manuellOppgaveService, syfoTilgangsKontrollClient) }
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

            with(handleRequest(HttpMethod.Get, "/api/v1/hentManuellOppgave/?oppgaveid=$oppgaveid") {
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
            }) {
                response.status() shouldEqual HttpStatusCode.OK
                objectMapper.readValue<List<ManuellOppgaveDTO>>(response.content!!).first().oppgaveid shouldEqual oppgaveid
            }
        }
    }

    @Test
    internal fun `Skal gi Bad Request, når oppgaveid mangler`() {
        with(TestApplicationEngine()) {
            start()

            database.opprettManuellOppgave(manuellOppgave, oppgaveid)

            application.routing { hentManuellOppgaver(manuellOppgaveService, syfoTilgangsKontrollClient) }
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

            with(handleRequest(HttpMethod.Get, "/api/v1/hentManuellOppgave/?feilparamanter=$oppgaveid")) {
                response.status() shouldEqual HttpStatusCode.BadRequest
                response.content shouldEqual null
            }
        }
    }

    @Test
    internal fun `Skal returnere ein tom liste av oppgaver, når det ikkje finnes noen oppgaver med opggit id`() {
        with(TestApplicationEngine()) {
            start()

            application.routing { hentManuellOppgaver(manuellOppgaveService, syfoTilgangsKontrollClient) }
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

            with(handleRequest(HttpMethod.Get, "/api/v1/hentManuellOppgave/?oppgaveid=$oppgaveid") {
                addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
            }) {
                response.status() shouldEqual HttpStatusCode.NoContent
                response.content!! shouldEqual "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveid"
            }
        }
    }
}
