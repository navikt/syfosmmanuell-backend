package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import no.nav.syfo.client.TexasClient
import no.nav.syfo.client.TexasToken
import no.nav.syfo.client.TilgangsmaskinClient
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.junit.jupiter.api.Assertions.assertEquals

class TilgangsmaskinClientTest :
    FunSpec({
        val httpClient = HttpClientTest()
        val environment = mockk<Environment>()
        val texasClient = mockk<TexasClient>()

        val pasientFnr = "123145"

        coEvery { environment.tilgangsmaskinUrl } returns "http://foo"
        coEvery { environment.tilgangsmaskinScope } returns "scope"
        coEvery { texasClient.exchangeToken(any(), any()) } returns TexasToken("obo-token")

        val tilgangsmaskinClient =
            spyk(
                TilgangsmaskinClient(
                    environment = environment,
                    httpClient = httpClient.httpClient,
                    texasClient = texasClient
                ),
            )

        beforeTest {
            clearMocks(texasClient)
            coEvery { texasClient.exchangeToken(any(), any()) } returns TexasToken("obo-token")
            tilgangsmaskinClient.tilgangsmaskinCache.invalidateAll()
        }

        context("Tilgangskontroll-test") {
            test("Skal returnere erGodkjent = true") {
                httpClient.responseData = ResponseData(HttpStatusCode.NoContent, "")

                val tilgang =
                    tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", pasientFnr)

                assertEquals(true, tilgang.erGodkjent)
            }
            test("Skal returnere erGodkjent = false hvis tilgangsmaskin svarer med 403") {
                httpClient.responseData = ResponseData(HttpStatusCode.Forbidden, "")

                val tilgang =
                    tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", pasientFnr)

                assertEquals(false, tilgang.erGodkjent)
            }
        }

        context("Test av cache") {
            test("Henter fra cache hvis kallet er cachet") {
                httpClient.responseData = ResponseData(HttpStatusCode.NoContent, "")

                tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", pasientFnr)
                tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", pasientFnr)

                coVerify(exactly = 1) { texasClient.exchangeToken("scope", "sdfsdfsfs") }
            }
            test("Henter ikke fra cache hvis samme accesstoken men ulikt fnr") {
                httpClient.responseData = ResponseData(HttpStatusCode.NoContent, "")

                tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", pasientFnr)
                tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", "987654")

                coVerify(exactly = 2) { texasClient.exchangeToken("scope", "sdfsdfsfs") }
            }
            test("Henter ikke fra cache hvis samme fnr men ulikt accesstoken") {
                httpClient.responseData = ResponseData(HttpStatusCode.NoContent, "")

                tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("sdfsdfsfs", pasientFnr)
                tilgangsmaskinClient.sjekkVeiledersTilgangTilPerson("xxxxxxxxx", pasientFnr)

                coVerify(exactly = 1) { texasClient.exchangeToken("scope", "sdfsdfsfs") }
                coVerify(exactly = 1) { texasClient.exchangeToken("scope", "xxxxxxxxx") }
            }
        }
    })
