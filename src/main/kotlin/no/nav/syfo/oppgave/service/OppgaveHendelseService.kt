package no.nav.syfo.oppgave.service

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.*
import no.nav.syfo.oppgave.kafka.OppgaveKafkaAivenRecord
import no.nav.syfo.oppgave.kafka.manuellOppgaveStatus
import no.nav.syfo.persistering.db.oppdaterOppgaveHendelse
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.persistering.db.slettOppgave
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.retry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.readValue

class OppgaveHendelseService(
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
) {
    val jsonMapper: JsonMapper = jacksonMapperBuilder().build()

    companion object {
        private val log = LoggerFactory.getLogger(OppgaveHendelseService::class.java)
    }

    suspend fun handleOppgaveHendelse(consumerRecord: ConsumerRecord<String, String>) {
        val oppgaveHendlese: OppgaveKafkaAivenRecord = jsonMapper.readValue(consumerRecord.value())
        val oppgaveStatus = oppgaveHendlese.hendelse.hendelsestype.manuellOppgaveStatus()
        val oppgaveId = oppgaveHendlese.oppgave.oppgaveId.toInt()

        val timestamp =
            oppgaveHendlese.hendelse.tidspunkt
                ?: run {
                    val kafkaTimestamp =
                        Instant.ofEpochMilli(consumerRecord.timestamp())
                            .atOffset(ZoneOffset.UTC)
                            .toLocalDateTime()
                    log.warn(
                        "Timestamp er null for oppgaveId: $oppgaveId, using kafka timestamp $kafkaTimestamp"
                    )
                    kafkaTimestamp
                }
        val eksisterendeManuellOppgave =
            database.hentKomplettManuellOppgave(oppgaveId).firstOrNull() ?: return
        val oppgaveFerdigstiltAvSyfosmmanuell =
            oppgaveHendlese.utfortAv?.navIdent == "syfosmmanuell-backend"
        val oppgaveFerdigstiltButNotInDB =
            !eksisterendeManuellOppgave.ferdigstilt &&
                oppgaveStatus == ManuellOppgaveStatus.FERDIGSTILT
        if (oppgaveFerdigstiltButNotInDB) {
            log.info("Oppgave er ferdigstilt but not updated in DB, oppgaveId: $oppgaveId")
            if (oppgaveFerdigstiltAvSyfosmmanuell) {
                log.info(
                    "oppgave is ferdigstilt by syfosmmanuell-backend, do not reopen, oppgaveId: $oppgaveId"
                )
            }
        }
        if (oppgaveFerdigstiltButNotInDB && !oppgaveFerdigstiltAvSyfosmmanuell) {
            val loggingMeta =
                eksisterendeManuellOppgave.receivedSykmelding.let {
                    LoggingMeta(
                        mottakId = it.navLogId,
                        orgNr = it.legekontorOrgNr,
                        msgId = it.msgId,
                        sykmeldingId = it.sykmelding.id,
                    )
                }
            log.info(
                "Gjenoppretter oppgave for oppgaveId: {} fra {} til APEN",
                oppgaveId,
                oppgaveStatus,
            )
            database.slettOppgave(oppgaveId)
            val oppgaveResponse =
                oppgaveService.gjenopprettOppgave(eksisterendeManuellOppgave, loggingMeta)
            val gjenopprettetManuellOppgave = eksisterendeManuellOppgave.toManuellOppgave()
            val statusTimestamp =
                oppgaveResponse.endretTidspunkt?.toLocalDateTime() ?: LocalDateTime.now()
            database.opprettManuellOppgave(
                gjenopprettetManuellOppgave,
                gjenopprettetManuellOppgave.apprec,
                oppgaveResponse.id,
                ManuellOppgaveStatus.APEN,
                statusTimestamp,
            )
        } else {
            log.info("Oppdaterer oppgave for oppgaveId: {} til {}", oppgaveId, oppgaveStatus)
            retry {
                database.oppdaterOppgaveHendelse(
                    oppgaveId = oppgaveId,
                    status = oppgaveStatus,
                    statusTimestamp = timestamp,
                )
            }
        }
    }
}
