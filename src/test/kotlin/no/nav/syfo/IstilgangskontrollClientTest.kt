package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.junit.jupiter.api.Assertions.assertEquals

class IstilgangskontrollClientTest :
    FunSpec({
        val httpClient = HttpClientTest()
        val environment = mockk<Environment>()
        val azureAdV2Client =
            spyk(AzureAdV2Client("foo", "bar", "http://obo", httpClient.httpClient))

        val pasientFnr = "123145"

        coEvery { environment.istilgangskontrollClientUrl } returns "http://foo"
        coEvery { environment.istilgangskontrollScope } returns "scope"

        val istilgangskontrollClient =
            spyk(
                IstilgangskontrollClient(
                    environment = environment,
                    httpClient = httpClient.httpClient,
                    azureAdV2Client = azureAdV2Client,
                ),
            )

        beforeTest {
            clearMocks(environment, azureAdV2Client)
            istilgangskontrollClient.istilgangskontrollCache.invalidateAll()
        }

        context("Tilgangskontroll-test") {
            test("Skal returnere erGodkjent = true") {
                httpClient.responseDataOboToken =
                    ResponseData(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            AzureAdV2TokenResponse("token", 1000000, "token_type")
                        ),
                    )
                httpClient.responseData =
                    ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true)))

                val tilgang =
                    istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                        "sdfsdfsfs",
                        pasientFnr
                    )

                assertEquals(true, tilgang.erGodkjent)
            }
            test(
                "Skal returnere erGodkjent = false hvis istilgangskontroll svarer med feilmelding"
            ) {
                httpClient.responseDataOboToken =
                    ResponseData(
                        HttpStatusCode.OK,
                        objectMapper.writeValueAsString(
                            AzureAdV2TokenResponse("token", 1000000, "token_type")
                        )
                    )
                httpClient.responseData =
                    ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false)))

                val tilgang =
                    istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                        "sdfsdfsfs",
                        pasientFnr
                    )

                assertEquals(false, tilgang.erGodkjent)
            }
        }

        context("Test av cache") {
            test("Henter fra cache hvis kallet er cachet") {
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    "sdfsdfsfs",
                    pasientFnr
                )
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    "sdfsdfsfs",
                    pasientFnr
                )

                coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
            }
            test("Henter ikke fra cache hvis samme accesstoken men ulikt fnr") {
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    "sdfsdfsfs",
                    pasientFnr
                )
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    "sdfsdfsfs",
                    "987654"
                )

                coVerify(exactly = 2) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
            }
            test("Henter ikke fra cache hvis samme fnr men ulikt accesstoken") {
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    "sdfsdfsfs",
                    pasientFnr
                )
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    "xxxxxxxxx",
                    pasientFnr
                )

                coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("sdfsdfsfs", "scope") }
                coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("xxxxxxxxx", "scope") }
            }
        }
    })
