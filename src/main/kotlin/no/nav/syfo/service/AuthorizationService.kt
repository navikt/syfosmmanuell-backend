package no.nav.syfo.service

import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Veileder
import no.nav.syfo.log

class AuthorizationService(
    private val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    suspend fun hasAccess(accessToken: String, pasientFnr: String): Boolean {
        val harTilgangTilOppgave =
                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                        accessToken,
                        pasientFnr
                )?.harTilgang

        return harTilgangTilOppgave != null && harTilgangTilOppgave
    }
    suspend fun getVeileder(accessToken: String): Veileder {
        val veilder = syfoTilgangsKontrollClient.hentVeilderIdentViaAzure(accessToken)
        if (veilder == null) {
            log.error("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
            throw IdentNotFoundException("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
        } else {
            return veilder
        }
    }
}

class IdentNotFoundException(override val message: String) : Exception(message)
