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
import java.lang.RuntimeException
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.GraphOboToken
import no.nav.syfo.client.GraphResponse
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.testutil.HttpClientTest
import no.nav.syfo.testutil.ResponseData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MSGraphClientTest : Spek({

    val httpClient = HttpClientTest()
    val environment = mockk<Environment>()
    val vault = mockk<VaultSecrets>()

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
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

    coEvery { environment.msGraphAadAccessTokenUrl } returns "http://obo"
    coEvery { environment.msGraphApiScope } returns "scope.ms"
    coEvery { vault.syfosmmanuellBackendClientId } returns "1234"
    coEvery { vault.syfosmmanuellBackendClientSecret } returns "secret"

    val msGraphClient = spyk(MSGraphClient(
            environment = environment,
            vault = vault,
            httpClient = httpClient.httpClient
    ))

    beforeEachTest {
        clearAllMocks()
        msGraphClient.subjectCache.invalidateAll()
    }

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    val accountName = "USERFOO123"

    describe("Get subect test") {

        it("Skal returnere subject") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphOboToken("token")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))
            runBlocking {
                val subjectFromMsGraph = msGraphClient.getSubjectFromMsGraph("usertoken")
                subjectFromMsGraph shouldEqual accountName
            }
        }
        it("Skal kaste RuntimeException hvis MS Graph API returnerer noe annet enn et gyldig GraphResponse") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphOboToken("token")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, "")

            assertFailsWith<RuntimeException> {
                runBlocking {
                    msGraphClient.getSubjectFromMsGraph("usertoken")
                }
            }
        }
        it("Skal kaste RuntimeException hvis MS Graph API returnerer statuskode != 200 OK") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphOboToken("token")))
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
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphOboToken("token")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            runBlocking {
                msGraphClient.getSubjectFromMsGraph("usertoken")
                msGraphClient.getSubjectFromMsGraph("usertoken")
            }

            coVerify(exactly = 1) { msGraphClient.exchangeAccessTokenForOnBehalfOfToken(any()) }
        }

        it("Henter ikke fra cache hvis ulikt accesstoken") {
            httpClient.responseDataOboToken = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphOboToken("token")))
            httpClient.responseData = ResponseData(HttpStatusCode.OK, objectMapper.writeValueAsString(GraphResponse(accountName)))

            runBlocking {
                msGraphClient.getSubjectFromMsGraph("usertoken")
                msGraphClient.getSubjectFromMsGraph("usertoken2")
            }

            coVerify(exactly = 2) { msGraphClient.exchangeAccessTokenForOnBehalfOfToken(any()) } }
    }
})
