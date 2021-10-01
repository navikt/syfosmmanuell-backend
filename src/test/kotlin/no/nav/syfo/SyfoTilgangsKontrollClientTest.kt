package no.nav.syfo

import org.spekframework.spek2.Spek

object SyfoTilgangsKontrollClientTest : Spek({

//    val httpClient = HttpClientTest()
//    val environment = mockk<Environment>()
//    val vault = mockk<VaultSecrets>()
//
//    val pasientFnr = "123145"
//
//    coEvery { environment.syfoTilgangsKontrollClientUrl } returns "http://foo"
//    coEvery { environment.aadAccessTokenUrl } returns "http://obo"
//    coEvery { environment.syfotilgangskontrollClientId } returns "1233"
//    coEvery { vault.syfosmmanuellBackendClientId } returns "1234"
//    coEvery { vault.syfosmmanuellBackendClientSecret } returns "secret"
//
//    val syfoTilgangsKontrollClient = spyk(SyfoTilgangsKontrollClient(
//            environment = environment,
//            vault = vault,
//            httpClient = httpClient.httpClient
//    ))
//
//    beforeEachTest {
//        clearAllMocks()
//        syfoTilgangsKontrollClient.syfoTilgangskontrollCache.invalidateAll()
//    }
//
//    describe("Tilgangskontroll-test") {
//
//        it("Skal returnere harTilgang = true") {
//            runBlocking {
//                httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AadAccessToken("token")))
//                httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(true, "")))
//                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
//                tilgang?.harTilgang shouldEqual true
//            }
//        }
//        it("Skal returnere harTilgang = false hvis syfotilgangskontroll svarer med feilmelding") {
//            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AadAccessToken("token")))
//            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(Tilgang(false, "har ikke tilgang")))
//            runBlocking {
//                val tilgang = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
//                tilgang?.harTilgang shouldEqual false
//            }
//        }
//    }
//    describe("Test av cache") {
//        it("Henter fra cache hvis kallet er cachet") {
//            runBlocking {
//                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
//                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
//            }
//
//            coVerify(exactly = 1) { syfoTilgangsKontrollClient.exchangeOboToken(any()) }
//        }
//        it("Henter ikke fra cache hvis samme accesstoken men ulikt fnr") {
//            runBlocking {
//                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
//                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", "987654")
//            }
//
//            coVerify(exactly = 2) { syfoTilgangsKontrollClient.exchangeOboToken(any()) }
//        }
//        it("Henter ikke fra cache hvis samme fnr men ulikt accesstoken") {
//            runBlocking {
//                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("sdfsdfsfs", pasientFnr)
//                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure("xxxxxxxxx", pasientFnr)
//            }
//
//            coVerify(exactly = 2) { syfoTilgangsKontrollClient.exchangeOboToken(any()) }
//        }
//    }
})
