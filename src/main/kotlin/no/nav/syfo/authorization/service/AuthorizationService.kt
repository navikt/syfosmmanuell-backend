package no.nav.syfo.authorization.service

import no.nav.syfo.authorization.db.getFnr
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.TilgangsmaskinClient
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.sikkerlogg

class AuthorizationService(
    val tilgangsmaskinClient: TilgangsmaskinClient,
    val istilgangskontrollClient: IstilgangskontrollClient,
    val msGraphClient: MSGraphClient,
    val databaseInterface: DatabaseInterface,
) {
    suspend fun hasAccess(oppgaveId: Int, accessToken: String): Boolean {
        val pasientFnr = databaseInterface.getFnr(oppgaveId)
        if (pasientFnr == null) {
            logger.info("did not find oppgave with id: $oppgaveId")
            return false
        }

        val harTilgangTilOppgave =
            istilgangskontrollClient
                .sjekkVeiledersTilgangTilPersonViaAzure(accessToken, pasientFnr)
                .erGodkjent

        val harTilgangTilgangsmaskin =
            tilgangsmaskinClient
                .sjekkVeiledersTilgangTilPerson(
                    accessToken = accessToken,
                    pasientFnr = pasientFnr,
                )
                .erGodkjent

        sikkerlogg.info(
            "Tilgangssjekk oppgaveId=$oppgaveId: " +
                "fødselsnummer=${pasientFnr}, : " +
                "tilgangsmaskin=$harTilgangTilgangsmaskin, " +
                "istilgangskontroll=$harTilgangTilOppgave, " +
                "forskjell=${harTilgangTilgangsmaskin != harTilgangTilOppgave}"
        )

        return harTilgangTilOppgave
    }

    suspend fun getVeileder(oppgaveId: Int, accessToken: String): String {
        try {
            return msGraphClient.getSubjectFromMsGraph(accessToken)
        } catch (e: Exception) {
            sikkerlogg.info(
                "Klarte ikke hente ut veilederIdent fra MS Graph API for oppgaveId $oppgaveId} " +
                    "med accessToken: $accessToken og Exception er",
                e
            )
            logger.error(
                "Klarte ikke hente ut veilederIdent fra MS Graph API for oppgaveId $oppgaveId}"
            )
            throw IdentNotFoundException(
                "Klarte ikke hente ut veilederIdent fra MS Graph API for oppgaveId $oppgaveId",
                e
            )
        }
    }
}

class IdentNotFoundException(override val message: String, cause: Throwable) :
    Exception(message, cause)
