package no.nav.syfo.oppgave.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.aksessering.db.finnesOppgave
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.oppgave.kafka.OppgaveKafkaAivenRecord
import no.nav.syfo.oppgave.kafka.manuellOppgaveStatus
import no.nav.syfo.persistering.db.oppdaterOppgaveHendelse
import org.slf4j.LoggerFactory

class OppgaveHendelseService(
    private val database: DatabaseInterface,
) {
    private val objectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    companion object {
        private val log = LoggerFactory.getLogger(OppgaveHendelseService::class.java)
    }

    suspend fun handleOppgaveHendelse(oppgaveHendleseInput: String) {
        val oppgaveHendlese: OppgaveKafkaAivenRecord = objectMapper.readValue(oppgaveHendleseInput)
        val oppgaveStatus = oppgaveHendlese.hendelse.hendelsestype.manuellOppgaveStatus()
        val timestamp = oppgaveHendlese.hendelse.tidspunkt
        val oppgaveId = oppgaveHendlese.oppgave.oppgaveId.toInt()
        if (database.finnesOppgave(oppgaveId)) {
            log.info("Oppdaterer oppgave for oppgaveId: {} til {}", oppgaveId, oppgaveStatus)
            database.oppdaterOppgaveHendelse(
                oppgaveId = oppgaveId,
                status = oppgaveStatus,
                statusTimestamp = timestamp,
            )
        }
    }
}
