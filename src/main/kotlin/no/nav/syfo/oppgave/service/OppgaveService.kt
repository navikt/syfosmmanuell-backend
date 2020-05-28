package no.nav.syfo.oppgave.service

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.FerdigStillOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.util.LoggingMeta

class OppgaveService(private val oppgaveClient: OppgaveClient) {
    suspend fun ferdigstillOppgave(manuellOppgave: ManuellOppgaveKomplett, loggingMeta: LoggingMeta) {
        val oppgaveVersjon = oppgaveClient.hentOppgave(manuellOppgave.oppgaveid, manuellOppgave.receivedSykmelding.msgId).versjon

        val ferdigStillOppgave = ferdigStillOppgave(manuellOppgave, oppgaveVersjon)

        val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, manuellOppgave.receivedSykmelding.msgId)

        log.info(
                "Ferdigstilter oppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                StructuredArguments.fields(loggingMeta)
        )
    }

    private fun ferdigStillOppgave(manuellOppgave: ManuellOppgaveKomplett, oppgaveVersjon: Int) = FerdigStillOppgave(
            versjon = oppgaveVersjon,
            id = manuellOppgave.oppgaveid,
            status = OppgaveStatus.FERDIGSTILT
    )
}
