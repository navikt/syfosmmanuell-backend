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
import no.nav.syfo.helpers.retry
import no.nav.syfo.log

class SyfoTilgangsKontrollClient constructor(
    private val url: String,
    private val httpClient: HttpClient
) {
    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(accessToken: String, personFnr: String): Tilgang? =
        retry("tilgang_til_person_via_azure") {
            val httpResponse = httpClient.get<HttpResponse>("$url/api/tilgang/bruker") {
            accept(ContentType.Application.Json)
            headers {
                append("Authorization", "Bearer $accessToken")
            }
            parameter("fnr", personFnr)
        }
            when (httpResponse.status) {
                HttpStatusCode.InternalServerError -> {
                    val internalServerErrorFeilmeldingTekst = "syfo-tilgangskontroll svarte med " +
                            "feilmeldingcode: ${HttpStatusCode.InternalServerError.value}, " +
                            "feilmeldingbeskrivelse: ${HttpStatusCode.InternalServerError.description}"
                    log.error(internalServerErrorFeilmeldingTekst)
                    Tilgang(
                        harTilgang = false,
                        begrunnelse = internalServerErrorFeilmeldingTekst
                    )
                }

                HttpStatusCode.BadRequest -> {
                    log.error("syfo-tilgangskontroll svarer med BadRequest")
                    return@retry null
                }

                HttpStatusCode.NotFound -> {
                    log.warn("syfo-tilgangskontroll svarer med NotFound")
                    return@retry null
                }

                HttpStatusCode.Unauthorized -> {
                    log.warn("syfo-tilgangskontroll svarer med Unauthorized")
                    return@retry Tilgang(
                        harTilgang = false,
                        begrunnelse = "syfo-tilgangskontroll svarer med Unauthorized"
                    )
                }

                else -> {
                    log.info("syfo-tilgangskontroll svarer med httpResponse status kode: {}", httpResponse.status.value)
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
