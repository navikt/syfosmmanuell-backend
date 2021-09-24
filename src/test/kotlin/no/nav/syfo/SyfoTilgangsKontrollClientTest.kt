package no.nav.syfo

import io.ktor.application.call
import io.ktor.application.install
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
import io.mockk.spyk
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AadAccessToken
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SyfoTilgangsKontrollClientTest : Spek({

    val httpClient = HttpClientTest()
    val environment = mockk<Environment>()
    val vault = mockk<VaultSecrets>()

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

    coEvery { environment.syfoTilgangsKontrollClientUrl } returns "http://foo"
    coEvery { environment.aadAccessTokenUrl } returns "http://obo"
    coEvery { environment.syfotilgangskontrollClientId } returns "1233"
    coEvery { vault.syfosmmanuellBackendClientId } returns "1234"
    coEvery { vault.syfosmmanuellBackendClientSecret } returns "secret"

    val syfoTilgangsKontrollClient = spyk(SyfoTilgangsKontrollClient(
            environment = environment,
            vault = vault,
            httpClient = httpClient.httpClient
    ))

    beforeEachTest {
        clearAllMocks()
        syfoTilgangsKontrollClient.syfoTilgangskontrollCache.invalidateAll()
    }

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    describe("Tilgangskontroll-test") {

        it("Skal returnere harTilgang = true") {
            runBlocking {
                httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AadAccessToken("token")))
                httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true, "")))
                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                tilgang?.harTilgang shouldEqual true
            }
        }
        it("Skal returnere harTilgang = false hvis syfotilgangskontroll svarer med feilmelding") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AadAccessToken("token")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false, "har ikke tilgang")))
//            coEvery { accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(any(), any()) } returns "annetToken"
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

            coVerify(exactly = 1) { syfoTilgangsKontrollClient.exchangeOboToken(any()) }
        }
        it("Henter ikke fra cache hvis samme accesstoken men ulikt fnr") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", "987654")
            }

            coVerify(exactly = 2) { syfoTilgangsKontrollClient.exchangeOboToken(any()) }
        }
        it("Henter ikke fra cache hvis samme fnr men ulikt accesstoken") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("xxxxxxxxx", pasientFnr)
            }

            coVerify(exactly = 2) { syfoTilgangsKontrollClient.exchangeOboToken(any()) }
        }
    }
})
