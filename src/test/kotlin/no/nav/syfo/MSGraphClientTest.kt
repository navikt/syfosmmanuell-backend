package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.azuread.v2.AzureAdV2TokenResponse
import no.nav.syfo.client.GraphOboToken
import no.nav.syfo.client.GraphResponse
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldBeEqualTo
import kotlin.test.assertFailsWith

class MSGraphClientTest : FunSpec({

    val httpClient = HttpClientTest()
    val environment = mockk<Environment>()
    val azureAdV2Client = spyk(AzureAdV2Client("foo", "bar", "http://obo", httpClient.httpClient))

    coEvery { environment.msGraphApiScope } returns "scope.ms"
    coEvery { environment.msGraphApiUrl } returns "http://msgraphfoo"

    val msGraphClient = spyk(
        MSGraphClient(
            environment = environment,
            azureAdV2Client = azureAdV2Client,
            httpClient = httpClient.httpClient
        )
    )

    val accountName = "USERFOO123"

    context("Get subject test") {
        beforeTest {
            clearMocks(environment, azureAdV2Client)
            msGraphClient.subjectCache.invalidateAll()
        }

        test("Skal returnere subject") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            val subjectFromMsGraph = msGraphClient.getSubjectFromMsGraph("usertoken")
            subjectFromMsGraph shouldBeEqualTo accountName
        }
        test("Skal kaste RuntimeException hvis MS Graph API returnerer noe annet enn et gyldig GraphResponse") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, "")

            assertFailsWith<RuntimeException> {
                runBlocking {
                    msGraphClient.getSubjectFromMsGraph("usertoken")
                }
            }
        }
        test("Skal kaste RuntimeException hvis MS Graph API returnerer statuskode != 200 OK") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.Forbidden, objectMapper.writeValueAsString(GraphOboToken("token")))

            assertFailsWith<RuntimeException> {
                runBlocking {
                    msGraphClient.getSubjectFromMsGraph("usertoken")
                }
            }
        }
    }
    context("Test av cache") {
        beforeTest {
            clearMocks(environment, azureAdV2Client)
            msGraphClient.subjectCache.invalidateAll()
        }
        test("Henter fra cache hvis kallet er cachet") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            msGraphClient.getSubjectFromMsGraph("usertoken")
            msGraphClient.getSubjectFromMsGraph("usertoken")

            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("usertoken", "scope.ms") }
        }

        test("Henter ikke fra cache hvis ulikt accesstoken") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(AzureAdV2TokenResponse("token", 1000000, "token_type")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            msGraphClient.getSubjectFromMsGraph("usertoken")
            msGraphClient.getSubjectFromMsGraph("usertoken2")

            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("usertoken", "scope.ms") }
            coVerify(exactly = 1) { azureAdV2Client.getOnBehalfOfToken("usertoken2", "scope.ms") }
        }
    }
})
