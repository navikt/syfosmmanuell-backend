package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import no.nav.syfo.log

class SyfoTilgangsKontrollClient(
    private val url: String,
    private val httpClient: HttpClient,
    private val syfotilgangskontrollClientId: String,
    private val accessTokenClient: AccessTokenClient
) {
    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(accessToken: String, personFnr: String): Tilgang? {
        val oboToken = accessTokenClient.hentOnBehalfOfTokenForInnloggetBruker(accessToken = accessToken, scope = syfotilgangskontrollClientId)
        val httpResponse = httpClient.get<HttpStatement>("$url/api/tilgang/navident/bruker/$personFnr") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $oboToken")
            }
        }.execute()
        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("syfo-tilgangskontroll svarte med InternalServerError")
                Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarte med InternalServerError"
                )
            }
            HttpStatusCode.BadRequest -> {
                log.error("syfo-tilgangskontroll svarer med BadRequest")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med BadRequest"
                )
            }
            HttpStatusCode.NotFound -> {
                log.warn("syfo-tilgangskontroll svarer med NotFound")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med NotFound"
                )
            }
            HttpStatusCode.Unauthorized -> {
                log.warn("syfo-tilgangskontroll svarer med Unauthorized")
                return Tilgang(
                    harTilgang = false,
                    begrunnelse = "syfo-tilgangskontroll svarer med Unauthorized"
                )
            }
        }
        log.info("syfo-tilgangskontroll svarer med httpResponse status kode: {}", httpResponse.status.value)
        log.info("Sjekker tilgang for veileder på person")
        return httpResponse.call.response.receive<Tilgang>()
    }
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)
