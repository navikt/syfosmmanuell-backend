package no.nav.syfo.authorization.service

import no.nav.syfo.authorization.db.getFnr
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log

class AuthorizationService(
    val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient,
    val msGraphClient: MSGraphClient,
    val databaseInterface: DatabaseInterface
) {
    suspend fun hasAccess(oppgaveId: Int, accessToken: String): Boolean {
        val pasientFnr = databaseInterface.getFnr(oppgaveId)
        if (pasientFnr == null) {
            log.info("did not find oppgave with id: $oppgaveId")
            return false
        }
        return syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(accessToken, pasientFnr)
                ?.harTilgang
                ?: false
    }

    suspend fun getVeileder(oppgaveId: Int, accessToken: String): String {
        val veileder = msGraphClient.getSubjectFromMsGraph(accessToken)
        if (veileder == null) {
            log.error("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
            throw IdentNotFoundException("Klarte ikke hente ut veilederident fra syfo-tilgangskontroll")
        } else {
            return veileder
        }
    }
}

class IdentNotFoundException(override val message: String) : Exception(message)
