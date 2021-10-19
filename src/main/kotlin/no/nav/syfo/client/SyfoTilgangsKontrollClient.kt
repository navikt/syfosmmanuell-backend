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
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.log

class SyfoTilgangsKontrollClient(
    environment: Environment,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val syfoTilgangsKontrollClientUrl: String = environment.syfoTilgangsKontrollClientUrl,
    private val scope: String = environment.syfotilgangskontrollScope
) {
    val syfoTilgangskontrollCache: Cache<Map<String, String>, Tilgang> = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<Map<String, String>, Tilgang>()
            
    companion object {
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(accessToken: String, personFnr: String): Tilgang? {
        syfoTilgangskontrollCache.getIfPresent(mapOf(Pair(accessToken, personFnr)))?.let {
            log.debug("Traff cache for syfotilgangskontroll")
            return it
        }
        val oboToken = azureAdV2Client.getOnBehalfOfToken(token = accessToken, scope = scope)?.accessToken
                ?: throw RuntimeException("Klarte ikke hente nytt accessToken for veileder ved tilgangssjekk")

        val httpResponse = httpClient.get<HttpStatement>("$syfoTilgangsKontrollClientUrl/api/tilgang/navident/person") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $oboToken")
                append(NAV_PERSONIDENT_HEADER, personFnr)
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
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)
