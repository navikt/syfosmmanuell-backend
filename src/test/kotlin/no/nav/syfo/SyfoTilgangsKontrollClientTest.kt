package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SyfoTilgangsKontrollClientTest : Spek({
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val accessTokenClient = mockk<AccessTokenClient>()

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val pasientFnr = "123145"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/api/tilgang/navident/bruker/$pasientFnr") {
                when {
                    call.request.headers["Authorization"] == "Bearer token" -> call.respond(
                        Tilgang(
                            harTilgang = true,
                            begrunnelse = null
                        )
                    )
                    else -> call.respond(HttpStatusCode.InternalServerError, "Noe gikk galt")
                }
            }
        }
    }.start()

    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
            url = mockHttpServerUrl,
            accessTokenClient = accessTokenClient,
            syfotilgangskontrollClientId = "syfo",
            httpClient = httpClient
    )

    beforeEachTest {
        clearAllMocks()
        syfoTilgangsKontrollClient.syfoTilgangskontrollCache.invalidateAll()
        coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "token"
    }

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Tilgangskontroll-test") {
        it("Skal returnere harTilgang = true") {
            runBlocking {
                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                tilgang?.harTilgang shouldEqual true
            }
        }
        it("Skal returnere harTilgang = false hvis syfotilgangskontroll svarer med feilmelding") {
            coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "annetToken"
            runBlocking {
                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                tilgang?.harTilgang shouldEqual false
            }
        }
    }
    describe("Test av cache") {
        it("Henter fra cache hvis kallet er cachet") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            }

            coVerify(exactly = 1) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
        }
        it("Henter ikke fra cache hvis samme accesstoken men ulikt fnr") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", "987654")
            }

            coVerify(exactly = 2) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
        }
        it("Henter ikke fra cache hvis samme fnr men ulikt accesstoken") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("xxxxxxxxx", pasientFnr)
            }

            coVerify(exactly = 2) { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) }
        }
    }
})
