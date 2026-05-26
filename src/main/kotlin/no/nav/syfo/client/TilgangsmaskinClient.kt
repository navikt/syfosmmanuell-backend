package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

class TilgangsmaskinClient(
    private val azureAdClient: AzureAdClient,
    private val baseUrl: String,
    private val clientId: String,
    private val httpClient: HttpClient = httpClientProxy(),
) {


    private val tilgangsmaskinUrl: String = "$baseUrl/api/v1/komplett"
    private val tilgangsmaskinBulkUrl: String = "$baseUrl/api/v1/bulk/obo"

    suspend fun hasTilgang(
        token: Token,
        personident: Personident,
        callId: String,
    ): TilgangsmaskinTilgang {
        val oboToken = azureAdClient.getOnBehalfOfToken(
            scopeClientId = clientId,
            token = token,
            callId = callId
        ) ?: throw RuntimeException("Could not get oboToken from AzureAd")

        val response = try {
            httpClient.post(tilgangsmaskinUrl) {
                header(HttpHeaders.Authorization, bearerHeader(oboToken.accessToken))
                header(NAV_CALL_ID_HEADER, callId)
                setBody(personident.value)
                accept(ContentType.Application.Json)
            }
        } catch (exc: ClientRequestException) {
            exc.response
        }
        val hasAccess = response.status == HttpStatusCode.NoContent
        return TilgangsmaskinTilgang(
            hasAccess = hasAccess,
            problemDetailResponse = if (hasAccess) null else response.body() as ProblemDetailResponse?,
        )
    }
}