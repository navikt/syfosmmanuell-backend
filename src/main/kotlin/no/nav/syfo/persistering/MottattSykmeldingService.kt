package no.nav.syfo.persistering

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Merknad
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.ClusterAuthorizationException
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class MottattSykmeldingService(
    private val kafkaAivenConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val topicAiven: String,
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
    private val manuellOppgaveService: ManuellOppgaveService
) {

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_TIME_SECONDS = 10L
    }

    @ExperimentalTime
    suspend fun startAivenConsumer() {
        while (applicationState.ready) {
            try {
                runAivenConsumer()
            } catch (ex: Exception) {
                when (ex) {
                    is AuthorizationException, is ClusterAuthorizationException -> {
                        throw ex
                    }
                    else -> {
                        log.error("Aiven: Caught exception, unsubscribing and retrying", ex)
                        kafkaAivenConsumer.unsubscribe()
                        delay(DELAY_ON_ERROR_SECONDS.seconds)
                    }
                }
            }
        }
    }

    private suspend fun runAivenConsumer() {
        kafkaAivenConsumer.subscribe(listOf(topicAiven))
        log.info("Starting consuming topic $topicAiven")
        while (applicationState.ready) {
            kafkaAivenConsumer.poll(Duration.ofSeconds(POLL_TIME_SECONDS)).forEach { consumerRecord ->
                if (consumerRecord.value() == null) {
                    log.info("Mottatt tombstone for sykmelding med id ${consumerRecord.key()}")
                    manuellOppgaveService.slettOppgave(consumerRecord.key())
                } else {
                    val receivedManuellOppgave: ManuellOppgave = objectMapper.readValue(consumerRecord.value())
                    val loggingMeta = LoggingMeta(
                        mottakId = receivedManuellOppgave.receivedSykmelding.navLogId,
                        orgNr = receivedManuellOppgave.receivedSykmelding.legekontorOrgNr,
                        msgId = receivedManuellOppgave.receivedSykmelding.msgId,
                        sykmeldingId = receivedManuellOppgave.receivedSykmelding.sykmelding.id
                    )
                    val receivedManuellOppgaveMedMerknad = receivedManuellOppgave.copy(
                        receivedSykmelding = receivedManuellOppgave.receivedSykmelding.copy(
                            merknader = listOf(
                                Merknad(
                                    type = "UNDER_BEHANDLING",
                                    beskrivelse = "Sykmeldingen er til manuell behandling"
                                )
                            )
                        )
                    )

                    handleReceivedMessage(
                        receivedManuellOppgaveMedMerknad,
                        loggingMeta
                    )
                }
            }
        }
    }

    suspend fun handleReceivedMessage(
        manuellOppgave: ManuellOppgave,
        loggingMeta: LoggingMeta
    ) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok en manuell oppgave, {}", fields(loggingMeta))
            INCOMING_MESSAGE_COUNTER.inc()

            if (database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
                log.warn(
                    "Manuell oppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                    manuellOppgave.receivedSykmelding.sykmelding.id,
                    fields(loggingMeta)
                )
            } else {
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
                MESSAGE_STORED_IN_DB_COUNTER.inc()
            }
        }
    }
}
