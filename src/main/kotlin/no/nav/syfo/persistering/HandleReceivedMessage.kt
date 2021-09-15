package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

@KtorExperimentalAPI
suspend fun handleReceivedMessage(
    manuellOppgave: ManuellOppgave,
    loggingMeta: LoggingMeta,
    database: DatabaseInterface,
    oppgaveService: OppgaveService,
    manuellOppgaveService: ManuellOppgaveService
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell oppgave, {}", fields(loggingMeta))
        INCOMING_MESSAGE_COUNTER.inc()

        if (database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
            log.warn(
                "Manuell oppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                manuellOppgave.receivedSykmelding.sykmelding.id, fields(loggingMeta)
            )
        } else {
            try {
                val oppgaveId = oppgaveService.opprettOppgave(manuellOppgave, loggingMeta)
                val oppdatertApprec = manuellOppgaveService.lagOppdatertApprec(manuellOppgave)

                database.opprettManuellOppgave(manuellOppgave, oppdatertApprec, oppgaveId)
                log.info(
                    "Manuell oppgave lagret i databasen, for {}, {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId),
                    fields(loggingMeta)
                )
                manuellOppgaveService.sendApprec(oppgaveId, oppdatertApprec, loggingMeta)
                manuellOppgaveService.sendReceivedSykmelding(manuellOppgave.receivedSykmelding, loggingMeta)
                manuellOppgaveService.sendToSyfoService(manuellOppgave.receivedSykmelding, loggingMeta)
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            } catch (e: Exception) {
                log.error("Noe gikk galt ved oppretting av oppgave: {}, {}", e.message, fields(loggingMeta))
                throw e
            }
        }
    }
}
