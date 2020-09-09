package no.nav.syfo.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.github.benmanes.caffeine.cache.Cache
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import java.time.Instant
import no.nav.syfo.log

class AccessTokenClient(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val httpClient: HttpClient,
    private val aadCache: Cache<Map<String, String>, String>
) {
    suspend fun hentOnBehalfOfTokenForInnloggetBruker(accessToken: String, scope: String): String {
        aadCache.getIfPresent(mapOf(Pair(accessToken, scope)))?.let {
            log.debug("traff cache for AAD")
            return it
        }
        log.info("Henter OBO-token fra Azure AD")
        val response: AadAccessToken = httpClient.post(aadAccessTokenUrl) {
            accept(ContentType.Application.Json)
            method = HttpMethod.Post
            body = FormDataContent(Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("resource", scope)
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("requested_token_use", "on_behalf_of")
                append("assertion", accessToken)
                append("assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            })
        }
        log.debug("Har hentet OBO-accesstoken")
        val oboToken = response.access_token
        aadCache.put(mapOf(Pair(accessToken, scope)), oboToken)
        return oboToken
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessToken(
    val access_token: String,
    val expires_on: Instant
)
