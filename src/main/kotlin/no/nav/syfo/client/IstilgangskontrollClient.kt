package no.nav.syfo.client

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger

class IstilgangskontrollClient(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val istilgangskontrollClientUrl: String = environment.istilgangskontrollClientUrl,
    private val scope: String = environment.istilgangskontrollScope,
) {
    val istilgangskontrollCache: Cache<Map<String, String>, Tilgang> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<Map<String, String>, Tilgang>()

    companion object {
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(
        accessToken: String,
        personFnr: String
    ): Tilgang {
        istilgangskontrollCache.getIfPresent(mapOf(Pair(accessToken, personFnr)))?.let {
            logger.debug("Traff cache for istilgangskontroll")
            return it
        }
        val oboToken =
            azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope).accessToken

        val httpResponse =
            httpClient.get("$istilgangskontrollClientUrl/api/tilgang/navident/person") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $oboToken")
                    append(NAV_PERSONIDENT_HEADER, personFnr)
                }
            }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                logger.debug(
                    "istilgangskontroll svarer med httpResponse status kode: {}",
                    httpResponse.status.value
                )
                logger.info("Sjekker tilgang for veileder på person")
                val tilgang = httpResponse.body<Tilgang>()
                istilgangskontrollCache.put(mapOf(Pair(accessToken, personFnr)), tilgang)
                tilgang
            }
            HttpStatusCode.Forbidden -> {
                logger.warn("istilgangskontroll svarte med ${httpResponse.status.value}")
                Tilgang(
                    erGodkjent = false,
                )
            }
            else -> {
                logger.error("istilgangskontroll svarte med ${httpResponse.status.value}")
                Tilgang(
                    erGodkjent = false,
                )
            }
        }
    }
}

data class Tilgang(
    val erGodkjent: Boolean,
)
