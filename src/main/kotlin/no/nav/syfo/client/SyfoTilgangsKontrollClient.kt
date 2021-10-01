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
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log

class SyfoTilgangsKontrollClient(
    environment: Environment,
    vault: VaultSecrets,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val syfoTilgangsKontrollClientUrl: String = environment.syfoTilgangsKontrollClientUrl,
    private val aadAccessTokenUrl: String = environment.aadAccessTokenUrl,
    private val scope: String = environment.syfotilgangskontrollClientId,
    private val clientId: String = vault.syfosmmanuellBackendClientId,
    private val clientSecret: String = vault.syfosmmanuellBackendClientSecret
) {
    val syfoTilgangskontrollCache: Cache<Map<String, String>, Tilgang> = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<Map<String, String>, Tilgang>()

    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(accessToken: String, personFnr: String): Tilgang? {
        syfoTilgangskontrollCache.getIfPresent(mapOf(Pair(accessToken, personFnr)))?.let {
            log.debug("Traff cache for syfotilgangskontroll")
            return it
        }
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope)

        val url = syfoTilgangsKontrollClientUrl
        val httpResponse = httpClient.get<HttpStatement>("$url/api/tilgang/navident/bruker/$personFnr") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer ${oboToken?.accessToken}")
            }
        }.execute()
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                log.debug("syfo-tilgangskontroll svarer med httpResponse status kode: {}", httpResponse.status.value)
                log.info("Sjekker tilgang for veileder på person")
                val tilgang = httpResponse.call.response.receive<Tilgang>()
                syfoTilgangskontrollCache.put(mapOf(Pair(accessToken, personFnr)), tilgang)
                return tilgang
            }
            else -> {
                    log.error("syfo-tilgangskontroll svarte med ${httpResponse.status.value}")
                    return Tilgang(
                            harTilgang = false,
                            begrunnelse = "syfo-tilgangskontroll svarte med ${HttpStatusCode.fromValue(httpResponse.status.value)}"
                    )
            }
        }
    }

//    suspend fun exchangeOboToken(accessToken: String): String {
//        log.info("Henter OBO-token fra Azure AD")
//        val response: AadAccessToken = httpClient.post(aadAccessTokenUrl) {
//            accept(ContentType.Application.Json)
//            method = HttpMethod.Post
//            body = FormDataContent(Parameters.build {
//                append("client_id", clientId)
//                append("client_secret", clientSecret)
//                append("resource", oboTokenScope)
//                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
//                append("requested_token_use", "on_behalf_of")
//                append("assertion", accessToken)
//                append("assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
//            })
//        }
//        log.debug("Har hentet OBO-accesstoken")
//        return response.access_token
//    }
}

data class AadAccessToken(val access_token: String)

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)
