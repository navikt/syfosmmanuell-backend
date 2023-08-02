package no.nav.syfo.oppgave.kafka

import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import no.nav.syfo.aksessering.db.finnesOppgave
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.persistering.db.oppdaterOppgaveHendelse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.ClusterAuthorizationException
import org.slf4j.LoggerFactory

class OppgaveHendelseConsumer(
    private val kafkaConsumer: KafkaConsumer<String, OppgaveKafkaAivenRecord>,
    private val topic: String,
    private val applicationState: ApplicationState,
    private val database: DatabaseInterface,
) {

    companion object {
        private val DELAY_ON_ERROR_SECONDS = 60.seconds
        private val POLL_TIME_DURATION = 10.seconds
        private val log = LoggerFactory.getLogger(OppgaveHendelseConsumer::class.java)
    }

    suspend fun start() {
        while (applicationState.ready) {
            try {
                kafkaConsumer.subscribe(listOf(topic))
                consumeMessages()
            } catch (ex: Exception) {
                handleException(ex)
            }
        }
    }

    private suspend fun consumeMessages() {
        while (applicationState.ready) {
            val records =
                withContext(Dispatchers.IO) {
                    kafkaConsumer.poll(POLL_TIME_DURATION.toJavaDuration())
                }
            records.forEach { record -> processRecord(record) }
        }
    }

    private suspend fun processRecord(record: ConsumerRecord<String, OppgaveKafkaAivenRecord>) {
        val oppgaveHendlese = record.value()
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

    private suspend fun handleException(exception: Exception) {
        when (exception) {
            is AuthorizationException,
            is ClusterAuthorizationException -> throw exception
            else -> {
                log.error("Aiven: Caught exception, unsubscribing and retrying", exception)
                kafkaConsumer.unsubscribe()
                delay(DELAY_ON_ERROR_SECONDS)
            }
        }
    }
}
