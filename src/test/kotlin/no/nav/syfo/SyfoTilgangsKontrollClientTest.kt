package no.nav.syfo

import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SyfoTilgangsKontrollClientTest : Spek({

    val httpClient = HttpClientTest()
    val environment = mockk<Environment>()
    val azureAdV2Client = spyk(AzureAdV2Client("foo", "bar", "http://obo", httpClient.httpClient))

    val pasientFnr = "123145"

    coEvery { environment.syfoTilgangsKontrollClientUrl } returns "http://foo"
    coEvery { environment.syfotilgangskontrollScope } returns "scope"

    val syfoTilgangsKontrollClient = spyk(
        SyfoTilgangsKontrollClient(
            environment = environment,
            httpClient = httpClient.httpClient,
            azureAdV2Client = azureAdV2Client
        )
    )

    beforeEachTest {
        clearMocks(environment, azureAdV2Client)
        syfoTilgangsKontrollClient.syfoTilgangskontrollCache.invalidateAll()
    }

    describe("Tilgangskontroll-test") {

        it("Skal returnere harTilgang = true") {
            runBlocking {
                httpClient.responseDataOboToken = ResponseData(
                    HttpStatusCode.OK,
                    objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type"))
                )
                httpClient.responseData =
                    ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))
                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                tilgang.harTilgang shouldBeEqualTo true
            }
        }
        it("Skal returnere harTilgang = false hvis syfotilgangskontroll svarer med feilmelding") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false)))
            runBlocking {
                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                tilgang.harTilgang shouldBeEqualTo false
            }
        }
    }
    describe("Test av cache") {
        it("Henter fra cache hvis kallet er cachet") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
            }

            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
        }
        it("Henter ikke fra cache hvis samme accesstoken men ulikt fnr") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", "987654")
            }

            coVerify(exactly = 2) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
        }
        it("Henter ikke fra cache hvis samme fnr men ulikt accesstoken") {
            runBlocking {
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("xxxxxxxxx", pasientFnr)
            }

            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("xxxxxxxxx", "scope") }
        }
    }
})
