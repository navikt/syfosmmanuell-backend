package no.nav.syfo.persistering

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toReceivedSykmeldingWithValidation
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

class MottattSykmeldingService(
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
    private val manuellOppgaveService: ManuellOppgaveService,
) {

    companion object {
        private val statusMap =
            mapOf(
                "FERDIGSTILT" to ManuellOppgaveStatus.FERDIGSTILT,
                "FEILREGISTRERT" to ManuellOppgaveStatus.FEILREGISTRERT,
                null to ManuellOppgaveStatus.DELETED,
            )
    }

    suspend fun handleMottattSykmelding(sykmeldingId: String, manuellOppgaveInput: String?) {
        if (manuellOppgaveInput == null) {
            logger.info("Mottatt tombstone for sykmelding med id $sykmeldingId")
            manuellOppgaveService.slettOppgave(sykmeldingId)
        } else {
            val receivedManuellOppgave: ManuellOppgave = objectMapper.readValue(manuellOppgaveInput)
            val loggingMeta =
                LoggingMeta(
                    mottakId = receivedManuellOppgave.receivedSykmelding.navLogId,
                    orgNr = receivedManuellOppgave.receivedSykmelding.legekontorOrgNr,
                    msgId = receivedManuellOppgave.receivedSykmelding.msgId,
                    sykmeldingId = receivedManuellOppgave.receivedSykmelding.sykmelding.id,
                )
            val receivedManuellOppgaveMedMerknad =
                receivedManuellOppgave.copy(
                    receivedSykmelding =
                        receivedManuellOppgave.receivedSykmelding.copy(
                            merknader =
                                listOf(
                                    Merknad(
                                        type = "UNDER_BEHANDLING",
                                        beskrivelse = "Sykmeldingen er til manuell behandling",
                                    ),
                                ),
                        ),
                )

            handleReceivedMessage(
                receivedManuellOppgaveMedMerknad,
                loggingMeta,
            )
        }
    }

    private suspend fun handleReceivedMessage(
        manuellOppgave: ManuellOppgave,
        loggingMeta: LoggingMeta,
    ) {
        wrapExceptions(loggingMeta) {
            logger.info("Mottok en manuell oppgave, {}", fields(loggingMeta))
            INCOMING_MESSAGE_COUNTER.inc()

            if (database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
                logger.warn(
                    "Manuell oppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                    manuellOppgave.receivedSykmelding.sykmelding.id,
                    fields(loggingMeta),
                )
            } else {
                val oppgave = oppgaveService.opprettOppgave(manuellOppgave, loggingMeta)
                val oppdatertApprec = manuellOppgaveService.lagOppdatertApprec(manuellOppgave)
                val status = statusMap[oppgave.status] ?: ManuellOppgaveStatus.APEN
                val statusTimestamp =
                    oppgave.endretTidspunkt?.toLocalDateTime() ?: LocalDateTime.now()
                database.opprettManuellOppgave(
                    manuellOppgave,
                    oppdatertApprec,
                    oppgave.id,
                    status,
                    statusTimestamp
                )
                logger.info(
                    "Manuell oppgave lagret i databasen, for {}, {}",
                    StructuredArguments.keyValue("oppgaveId", oppgave.id),
                    fields(loggingMeta),
                )
                manuellOppgaveService.sendApprec(oppgave.id, oppdatertApprec, loggingMeta)
                manuellOppgaveService.sendReceivedSykmelding(
                    manuellOppgave.receivedSykmelding.toReceivedSykmeldingWithValidation(
                        ValidationResult(
                            status = Status.OK,
                            ruleHits = emptyList(),
                            timestamp = OffsetDateTime.now(ZoneOffset.UTC)
                        )
                    ),
                    loggingMeta
                )
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            }
        }
    }
}
