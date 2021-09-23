package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import java.io.Serializable
import java.util.concurrent.TimeUnit
import no.nav.syfo.log

class MSGraphClient(
    private val scope: String,
    private val httpClient: HttpClient,
    private val accessTokenClient: AccessTokenClient
) {
    private val graphApiAccountNameQuery = "https://graph.microsoft.com/v1.0/me/?\$select=onPremisesSamAccountName"

    private val subjectCache: Cache<String, String> = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<String, String>()

    suspend fun getSubjectFromMsGraph(oppgaveId: Int, accessToken: String): String {

        subjectCache.getIfPresent(accessToken)?.let {
            log.debug("Traff subject cache for MSGraph")
            return it
        }

        return try {
            val oboToken = accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker2(accessToken = accessToken, scope = scope)
            val subject = callMsGraphApi(oppgaveId, oboToken)
            subjectCache.put(accessToken, subject)
            subject
        } catch (e: Exception) {
            throw RuntimeException("Klarte ikke hente veileder-ident fra MSGraph: ${e.message}")
        }
    }

    private suspend fun callMsGraphApi(oppgaveId: Int, oboToken: String): String {

        log.info("Querying MS Graph for oppgaveId $oppgaveId")

        val response = httpClient.get<HttpStatement>(graphApiAccountNameQuery) {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $oboToken")
            }
        }.execute()

        if (response.status == HttpStatusCode.OK) {
            return response.call.receive<GraphResponse>().onPremisesSamAccountName
        } else {
            throw RuntimeException("Noe gikk galt ved henting av veilderIdent fra Ms Graph ${response.status}")
        }
    }
}

data class GraphResponse(val onPremisesSamAccountName: String) : Serializable
