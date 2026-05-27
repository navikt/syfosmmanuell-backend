package no.nav.syfo.client

import com.auth0.jwt.JWT
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headers
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.logger
import no.nav.syfo.sikkerlogg

class TilgangsmaskinClient(
    private val environment: Environment,
    private val texasClient: TexasClient,
    private val httpClient: HttpClient,
    private val scope: String = environment.tilgangsmaskinScope,
    tilgangsmaskinClientUrl: String = environment.tilgangsmaskinUrl
) {
    private val tilgangsmaskinUrl: String = "$tilgangsmaskinClientUrl/api/v1/komplett"
    val tilgangsmaskinCache: Cache<Map<String, String>, Tilgang> =
        Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build<Map<String, String>, Tilgang>()

    suspend fun sjekkVeiledersTilgangTilPerson(
        accessToken: String,
        pasientFnr: String,
    ): Tilgang {
        tilgangsmaskinCache.getIfPresent(mapOf(Pair(accessToken, pasientFnr)))?.let {
            logger.debug("Traff cache for tilgangsmaskin")
            return it
        }

        val (oboToken) = exchange(accessToken)

        val decodedOboToken = JWT.decode(oboToken)
        sikkerlogg.info(
            "OBO-token for tilgangsmaskin: iss={}, aud={}, sub={}",
            decodedOboToken.issuer,
            decodedOboToken.audience,
            decodedOboToken.subject,
        )

        val httpResponse =
            httpClient.post(tilgangsmaskinUrl) {
                headers {
                    append("Authorization", "Bearer $oboToken")
                    append("Nav-Call-Id", pasientFnr)
                }
                contentType(ContentType.Application.Json)
                setBody(pasientFnr)
            }

        val responseBody = httpResponse.bodyAsText()
        sikkerlogg.info(
            "svar fra tilgangsmaskin {} for fnr {}: {}",
            httpResponse.status.value,
            pasientFnr,
            responseBody,
        )

        return when (httpResponse.status) {
            HttpStatusCode.NoContent -> {
                logger.info(
                    "tilgangsmaskin svarer med httpResponse status kode: {}",
                    httpResponse.status.value
                )
                val tilgang = Tilgang(erGodkjent = true)
                tilgangsmaskinCache.put(mapOf(Pair(accessToken, pasientFnr)), tilgang)
                tilgang
            }
            HttpStatusCode.Forbidden -> {
                logger.warn("tilgangsmaskin svarte med ${httpResponse.status.value}")
                Tilgang(
                    erGodkjent = false,
                )
            }
            HttpStatusCode.NotFound -> {
                logger.warn("tilgangsmaskin svarte med ${httpResponse.status.value}")
                Tilgang(
                    erGodkjent = false,
                )
            }
            else -> {
                logger.error("tilgangsmaskin svarte med ${httpResponse.status.value}")
                Tilgang(
                    erGodkjent = false,
                )
            }
        }
    }

    private suspend fun exchange(userToken: String) = texasClient.exchangeToken(scope, userToken)
}
