package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import java.io.Serializable
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.log

class MSGraphClient(
    environment: Environment,
    vault: VaultSecrets,
    private val httpClient: HttpClient,
    private val aadAccessTokenUrl: String = environment.msGraphAadAccessTokenUrl,
    private val oboScope: String = environment.msGraphApiScope,
    msGraphApiUrl: String = environment.msGraphApiUrl,
    private val clientId: String = vault.syfosmmanuellBackendClientId,
    private val clientSecret: String = vault.syfosmmanuellBackendClientSecret
) {

    private val msGraphApiAccountNameQuery = "$msGraphApiUrl/me/?\$select=onPremisesSamAccountName"

    val subjectCache: Cache<String, String> = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<String, String>()

    suspend fun getSubjectFromMsGraph(accessToken: String): String {

        subjectCache.getIfPresent(accessToken)?.let {
            log.debug("Traff subject cache for MSGraph")
            return it
        }

        return try {
            val oboToken = exchangeAccessTokenForOnBehalfOfToken(accessToken)
            val subject = callMsGraphApi(oboToken)
            subjectCache.put(accessToken, subject)
            subject
        } catch (e: Exception) {
            throw RuntimeException("Klarte ikke hente veileder-ident fra MSGraph: ", e)
        }
    }

    private suspend fun callMsGraphApi(oboToken: String): String {

        val response = httpClient.get<HttpStatement>(msGraphApiAccountNameQuery) {
            headers {
                append("Authorization", "Bearer $oboToken")
            }
        }.execute()

        if (response.status == HttpStatusCode.OK) {
            return response.call.receive<GraphResponse>().onPremisesSamAccountName
        } else {
            throw RuntimeException("Noe gikk galt ved henting av veilderIdent fra Ms Graph ${response.status} ${response.call.receive<String>()}")
        }
    }

    suspend fun exchangeAccessTokenForOnBehalfOfToken(accessToken: String): String {
        log.info("Henter OBO-token for MS Graph")
        val response: GraphOboToken = httpClient.post(aadAccessTokenUrl) {
            accept(ContentType.Application.Json)
            method = HttpMethod.Post
            body = FormDataContent(Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", oboScope)
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("requested_token_use", "on_behalf_of")
                append("assertion", accessToken)
                append("assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
            })
        }
        return response.access_token
    }
}

data class GraphOboToken(val access_token: String)
data class GraphResponse(val onPremisesSamAccountName: String) : Serializable
