package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import no.nav.syfo.helpers.retry

class SyfoTilgangsKontrollClient constructor(
    private val url: String,
    private val idToken: String,
    private val httpClient: HttpClient
) {
    suspend fun sjekkVeiledersTilgangTilPersonViaAzure(): Tilgang? =
        retry("tilgang_til_person_via_azure") {
        httpClient.get<Tilgang>("$url/bruker") {
            accept(ContentType.Application.Json)
            header("Authorization", "Bearer $idToken")
        }
    }
}

data class Tilgang(
    val harTilgang: Boolean,
    val begrunnelse: String
)
