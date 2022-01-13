package no.nav.syfo.oppgave.client

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.clients.HttpClients.Companion.config
import no.nav.syfo.oppgave.FerdigstillOppgave
import no.nav.syfo.oppgave.OppgaveStatus
import no.nav.syfo.oppgave.OpprettOppgave
import no.nav.syfo.oppgave.OpprettOppgaveResponse
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

object OppgaveClientTest : Spek({
    val httpClient = HttpClient(Apache) {
        config()
    }
    val oidcClient = mockk<StsOidcClient>()

    val opprettOppgave = OpprettOppgave(
        aktoerId = "5555",
        opprettetAvEnhetsnr = "9999",
        behandlesAvApplikasjon = "FS22",
        beskrivelse = "Manuell vurdering av sykmelding for periode: 01.08.2020 - 15.08.2020",
        tema = "SYM",
        oppgavetype = "BEH_EL_SYM",
        behandlingstype = "ae0239",
        aktivDato = LocalDate.now(),
        fristFerdigstillelse = LocalDate.now().plusDays(3),
        prioritet = "HOY"
    )

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            post("/oppgave") {
                when {
                    call.request.headers["X-Correlation-ID"] == "123" -> call.respond(HttpStatusCode.Created, OpprettOppgaveResponse(1, 1))
                    else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                }
            }
            patch("/oppgave/2") {
                call.respond(OpprettOppgaveResponse(2, 2))
            }
            patch("/oppgave/3") {
                call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
            }
            get("/oppgave/4") {
                call.respond(OpprettOppgaveResponse(4, 1))
            }
            get("/oppgave/5") {
                call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
            }
        }
    }.start()

    val oppgaveClient = OppgaveClient("$mockHttpServerUrl/oppgave", oidcClient, httpClient)

    beforeEachTest {
        clearMocks(oidcClient)
        coEvery { oidcClient.oidcToken() } returns OidcToken("token", "", 1L)
    }

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Test av opprettOppgave") {
        it("Oppretter oppgave") {
            var opprettOppgaveResponse: OpprettOppgaveResponse?
            runBlocking {
                opprettOppgaveResponse = oppgaveClient.opprettOppgave(opprettOppgave, "123")
            }

            opprettOppgaveResponse?.id shouldBeEqualTo 1
            opprettOppgaveResponse?.versjon shouldBeEqualTo 1
        }
        it("Kaster feil hvis oppretting feiler") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    oppgaveClient.opprettOppgave(opprettOppgave, "1234")
                }
            }
        }
    }

    describe("Test av ferdigstillOppgave") {
        it("Ferdigstiller oppgave") {
            var opprettOppgaveResponse: OpprettOppgaveResponse?
            runBlocking {
                opprettOppgaveResponse = oppgaveClient.ferdigstillOppgave(
                    FerdigstillOppgave(
                        id = 2,
                        versjon = 2,
                        status = OppgaveStatus.FERDIGSTILT,
                        tildeltEnhetsnr = "1234",
                        tilordnetRessurs = "4321",
                        mappeId = null
                    ),
                    "123"
                )
            }

            opprettOppgaveResponse?.id shouldBeEqualTo 2
            opprettOppgaveResponse?.versjon shouldBeEqualTo 2
        }
        it("Kaster feil hvis ferdigstilling feiler") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    oppgaveClient.ferdigstillOppgave(FerdigstillOppgave(id = 3, versjon = 2, status = OppgaveStatus.FERDIGSTILT, tildeltEnhetsnr = "1234", tilordnetRessurs = "4321", mappeId = null), "123")
                }
            }
        }
    }

    describe("Test av hentOppgave") {
        it("Henter oppgave") {
            var opprettOppgaveResponse: OpprettOppgaveResponse?
            runBlocking {
                opprettOppgaveResponse = oppgaveClient.hentOppgave(4, "123")
            }

            opprettOppgaveResponse?.id shouldBeEqualTo 4
            opprettOppgaveResponse?.versjon shouldBeEqualTo 1
        }
        it("Kaster feil hvis henting feiler") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    oppgaveClient.hentOppgave(5, "123")
                }
            }
        }
    }
})
