package no.nav.syfo

import io.ktor.http.HttpStatusCode
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import java.lang.RuntimeException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.client.GraphOboToken
import no.nav.syfo.client.GraphResponse
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MSGraphClientTest : Spek({

    val httpClient = HttpClientTest()
    val environment = mockk<Environment>()
    val vault = mockk<VaultSecrets>()
    val azureAdV2Client = spyk(AzureAdV2Client("foo", "bar", "http://obo", httpClient.httpClient))

    coEvery { environment.msGraphAadAccessTokenUrl } returns "http://obo"
    coEvery { environment.msGraphApiScope } returns "scope.ms"
    coEvery { environment.msGraphApiUrl } returns "http://msgraphfoo"
    coEvery { vault.syfosmmanuellBackendClientId } returns "1234"
    coEvery { vault.syfosmmanuellBackendClientSecret } returns "secret"

    val msGraphClient = spyk(MSGraphClient(
            environment = environment,
            azureAdV2Client = azureAdV2Client,
            httpClient = httpClient.httpClient
    ))

    beforeEachTest {
        clearAllMocks()
        msGraphClient.subjectCache.invalidateAll()
    }

    val accountName = "USERFOO123"

    describe("Get subect test") {

        it("Skal returnere subject") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))
            runBlocking {
                val subjectFromMsGraph = msGraphClient.getSubjectFromMsGraph("usertoken")
                subjectFromMsGraph shouldEqual accountName
            }
        }
        it("Skal kaste RuntimeException hvis MS Graph API returnerer noe annet enn et gyldig GraphResponse") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, "")

            assertFailsWith<RuntimeException> {
                runBlocking {
                    msGraphClient.getSubjectFromMsGraph("usertoken")
                }
            }
        }
        it("Skal kaste RuntimeException hvis MS Graph API returnerer statuskode != 200 OK") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.Forbidden, objectMapper.writeValueAsString(GraphOboToken("token")))

            assertFailsWith<RuntimeException> {
                runBlocking {
                    msGraphClient.getSubjectFromMsGraph("usertoken")
                }
            }
        }
    }
    describe("Test av cache") {
        it("Henter fra cache hvis kallet er cachet") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            runBlocking {
                msGraphClient.getSubjectFromMsGraph("usertoken")
                msGraphClient.getSubjectFromMsGraph("usertoken")
            }

            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken(any(), any()) }
        }

        it("Henter ikke fra cache hvis ulikt accesstoken") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            runBlocking {
                msGraphClient.getSubjectFromMsGraph("usertoken")
                msGraphClient.getSubjectFromMsGraph("usertoken2")
            }

            coVerify(exactly = 2) { azureAdV2Client.getOnBehalfOfToken(any(), any()) }
        }
    }
})
