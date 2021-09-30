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
        try {
            return msGraphClient.getSubjectFromMsGraph(accessToken)
        } catch (e: Exception) {
            log.error("Klarte ikke hente ut veilederident fra MS Graph API for oppgaveId $oppgaveId}")
            throw IdentNotFoundException("Klarte ikke hente ut MS Graph API fra syfo-tilgangskontroll for oppgaveId $oppgaveId, $e.message")
        }
    }
}

class IdentNotFoundException(override val message: String) : Exception(message)
