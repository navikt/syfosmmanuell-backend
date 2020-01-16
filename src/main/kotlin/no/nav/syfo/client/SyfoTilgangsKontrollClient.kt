package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import java.io.IOException
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

class SyfoTilgangsKontrollClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(idToken: String, personFnr: String): Tilgang? =
        retry("tilgang_til_person_via_azure") {
            val httpResponse = httpClient.get<HttpResponse>("$url/api/tilgang/bruker") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $idToken")
            }
            parameter("fnr", personFnr)
        }
            when (httpResponse.status) {
                HttpStatusCode.InternalServerError -> {
                    log.error("syfo-tilgangskontroll svarte med feilmelding")
                    throw IOException("syfo-tilgangskontroll svarte med feilmelding")
                }

                HttpStatusCode.BadRequest -> {
                    log.error("syfo-tilgangskontroll svarer med BadRequest")
                    return@retry null
                }

                HttpStatusCode.NotFound -> {
                    log.warn("syfo-tilgangskontroll svarer med NotFound")
                    return@retry null
                }
                else -> {
                    log.info("Sjekker tilgang for veileder på person")
                    httpResponse.call.response.receive<Tilgang>()
                }
            }
    }
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String?
)
