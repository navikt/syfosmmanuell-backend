package no.nav.syfo.oppgave.client

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.clients.HttpClients.Companion.config
import no.nav.syfo.oppgave.FerdigstillOppgave
import no.nav.syfo.oppgave.OppgaveStatus
import no.nav.syfo.oppgave.OpprettOppgave
import no.nav.syfo.oppgave.OpprettOppgaveResponse
import org.junit.jupiter.api.Assertions.assertEquals

class OppgaveClientTest :
    FunSpec({
        val httpClient = HttpClient(Apache) { config() }
        val azureAdV2Client = mockk<AzureAdV2Client>()

        val opprettOppgave =
            OpprettOppgave(
                aktoerId = "5555",
                opprettetAvEnhetsnr = "9999",
                behandlesAvApplikasjon = "FS22",
                beskrivelse =
                    "Manuell vurdering av sykmelding for periode: 01.08.2020 - 15.08.2020",
                tema = "SYM",
                oppgavetype = "BEH_EL_SYM",
                behandlingstype = "ae0239",
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = LocalDate.now().plusDays(3),
                prioritet = "HOY",
            )

        val mockHttpServerPort = ServerSocket(0).use { it.localPort }
        val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
        val mockServer =
            embeddedServer(Netty, mockHttpServerPort) {
                    install(ContentNegotiation) { jackson {} }
                    routing {
                        post("/oppgave") {
                            when {
                                call.request.headers["X-Correlation-ID"] == "123" ->
                                    call.respond(
                                        HttpStatusCode.Created,
                                        OpprettOppgaveResponse(1, 1)
                                    )
                                else ->
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        "Noe gikk galt"
                                    )
                            }
                        }
                        patch("/oppgave/2") { call.respond(OpprettOppgaveResponse(2, 2)) }
                        patch("/oppgave/3") {
                            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                        }
                        get("/oppgave/4") { call.respond(OpprettOppgaveResponse(4, 1)) }
                        get("/oppgave/5") {
                            call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                        }
                    }
                }
                .start()

        val oppgaveClient =
            OppgaveClient(
                "$mockHttpServerUrl/oppgave",
                azureAdV2Client,
                httpClient,
                "scope",
                "prod-gcp"
            )

        beforeTest {
            clearMocks(azureAdV2Client)
            coEvery { azureAdV2Client.getAccessToken(any()) } returns "token"
        }

        afterSpec { mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1)) }

        context("Test av opprettOppgave") {
            test("Oppretter oppgave") {
                val opprettOppgaveResponse = oppgaveClient.opprettOppgave(opprettOppgave, "123")

                assertEquals(1, opprettOppgaveResponse.id)
                assertEquals(1, opprettOppgaveResponse.versjon)
            }
            test("Kaster feil hvis oppretting feiler") {
                assertFailsWith<RuntimeException> {
                    runBlocking { oppgaveClient.opprettOppgave(opprettOppgave, "1234") }
                }
            }
        }

        context("Test av ferdigstillOppgave") {
            test("Ferdigstiller oppgave") {
                val opprettOppgaveResponse =
                    oppgaveClient.ferdigstillOppgave(
                        FerdigstillOppgave(
                            id = 2,
                            versjon = 2,
                            status = OppgaveStatus.FERDIGSTILT,
                            tildeltEnhetsnr = "1234",
                            tilordnetRessurs = "4321",
                            mappeId = null,
                        ),
                        "123",
                    )

                assertEquals(2, opprettOppgaveResponse.id)
                assertEquals(2, opprettOppgaveResponse.versjon)
            }
            test("Kaster feil hvis ferdigstilling feiler") {
                assertFailsWith<RuntimeException> {
                    runBlocking {
                        oppgaveClient.ferdigstillOppgave(
                            FerdigstillOppgave(
                                id = 3,
                                versjon = 2,
                                status = OppgaveStatus.FERDIGSTILT,
                                tildeltEnhetsnr = "1234",
                                tilordnetRessurs = "4321",
                                mappeId = null
                            ),
                            "123"
                        )
                    }
                }
            }
        }

        context("Test av hentOppgave") {
            test("Henter oppgave") {
                val opprettOppgaveResponse = oppgaveClient.hentOppgave(4, "123")

                assertEquals(4, opprettOppgaveResponse?.id)
                assertEquals(1, opprettOppgaveResponse?.versjon)
            }
            test("Kaster feil hvis henting feiler") {
                assertFailsWith<RuntimeException> {
                    runBlocking { oppgaveClient.hentOppgave(5, "123") }
                }
            }
        }
    })
