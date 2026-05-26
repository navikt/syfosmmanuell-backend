package no.nav.syfo.client

import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.isTextType
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.Environment
import no.nav.syfo.logger

data class TexasToken(val token: String)

class TexasClient(httpClient: HttpClient, private val environment: Environment) {
    private val texasHttpClient = httpClient.config { install(ContentNegotiation) { jackson {} } }

    suspend fun requestToken(
        scope: String,
    ): TexasToken {
        val requestBody = TokenRequest(identityProvider = "entra_id", target = scope)

        val response =
            texasHttpClient.post(environment.texasTokenEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

        if (!response.status.isSuccess()) {
            response.logNonSuccess(scope)
            throw IllegalStateException("Unable to request m2m token for: $scope")
        }

        val body = response.body<TokenResponse>()
        return TexasToken(body.accessToken)
    }

    private suspend fun HttpResponse.logNonSuccess(target: String) {
        if (this.contentType()?.isTextType() == true) {
            logger.error(
                "Unable to request m2m token for: ${target}, texas says: ${this.body<String>()}"
            )
        } else {
            logger.error(
                "Unable to request m2m token for: ${target}, texas responded with status ${this.status} and no content type"
            )
        }
    }

    internal data class TokenRequest(
        @param:JsonProperty("identity_provider") val identityProvider: String,
        val target: String,
    )

    internal data class TokenResponse(
        @param:JsonProperty("access_token") val accessToken: String,
        @param:JsonProperty("expires_in") val expiresIn: Int,
        @param:JsonProperty("token_type") val tokenType: String,
    )
}
