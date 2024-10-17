package no.nav.syfo.oppgave.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.Instant
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
import java.time.LocalDateTime

class OppgaveHendelseService(
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
) {
    private val objectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    companion object {
        private val log = LoggerFactory.getLogger(OppgaveHendelseService::class.java)
    }

    suspend fun handleOppgaveHendelse(consumerRecord: ConsumerRecord<String, String>) {
        val oppgaveHendlese: OppgaveKafkaAivenRecord =
            objectMapper.readValue(consumerRecord.value())
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
        val eksisterendeManuellOppgave = database.hentKomplettManuellOppgave(oppgaveId).firstOrNull() ?: return
        if (
            !eksisterendeManuellOppgave.ferdigstilt &&
                (oppgaveStatus == ManuellOppgaveStatus.FERDIGSTILT ||
                    oppgaveStatus == ManuellOppgaveStatus.FEILREGISTRERT)
        ) {
            val loggingMeta =
                eksisterendeManuellOppgave.receivedSykmelding.let {
                    LoggingMeta(
                        mottakId = it.navLogId,
                        orgNr = it.legekontorOrgNr,
                        msgId = it.msgId,
                        sykmeldingId = it.sykmelding.id
                    )
                }
            log.info(
                "Gjenoppretter oppgave for oppgaveId: {} fra {} til APEN",
                oppgaveId,
                oppgaveStatus
            )
            database.slettOppgave(oppgaveId)
            val oppgaveResponse = oppgaveService.gjenopprettOppgave(eksisterendeManuellOppgave, loggingMeta)
            val gjenopprettetManuellOppgave = eksisterendeManuellOppgave.toManuellOppgave()
            val statusTimestamp =
                oppgaveResponse.endretTidspunkt?.toLocalDateTime() ?: LocalDateTime.now()
            database.opprettManuellOppgave(
                gjenopprettetManuellOppgave,
                gjenopprettetManuellOppgave.apprec,
                oppgaveResponse.id,
                ManuellOppgaveStatus.APEN,
                statusTimestamp
            )
        } else {
            log.info("Oppdaterer oppgave for oppgaveId: {} til {}", oppgaveId, oppgaveStatus)
            retry {
                database.oppdaterOppgaveHendelse(
                    oppgaveId = oppgaveId,
                    status = oppgaveStatus,
                    statusTimestamp = timestamp
                )
            }
        }
    }
}
