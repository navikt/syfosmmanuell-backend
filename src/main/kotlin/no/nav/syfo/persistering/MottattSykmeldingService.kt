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
import no.nav.syfo.oppgave.exceptions.OpprettOppgaveException
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableOpprettOppgaveException
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class MottattSykmeldingService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val topic: String,
    private val database: DatabaseInterface,
    private val oppgaveService: OppgaveService,
    private val manuellOppgaveService: ManuellOppgaveService
) {

    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
    }

    @OptIn(ExperimentalTime::class)
    suspend fun startConsumer() {
        while (applicationState.ready) {
            try {
                runConsumer()
            } catch (ex: Exception) {
                when (ex) {
                    is TrackableOpprettOppgaveException -> {
                        log.warn("Caught {}, unsubscribing and retrying", ex.cause)
                        kafkaConsumer.unsubscribe()
                        delay(DELAY_ON_ERROR_SECONDS.seconds)
                    }
                    else -> {
                        throw ex
                    }
                }
            }
        }
    }

    private suspend fun runConsumer() {
        kafkaConsumer.subscribe(listOf(topic))
        log.info("Starting consuming topic $topic")
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofMillis(0)).forEach { consumerRecord ->
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
                            Merknad(type = "UNDER_BEHANDLING", beskrivelse = "Sykmeldingen er til manuell behandling")
                        )
                    )
                )

                handleReceivedMessage(
                    receivedManuellOppgaveMedMerknad,
                    loggingMeta,
                    database,
                    oppgaveService,
                    manuellOppgaveService
                )
            }
            delay(100)
        }
    }

    suspend fun handleReceivedMessage(
        manuellOppgave: ManuellOppgave,
        loggingMeta: LoggingMeta,
        database: DatabaseInterface,
        oppgaveService: OppgaveService,
        manuellOppgaveService: ManuellOppgaveService
    ) {
        wrapExceptions(loggingMeta) {
            log.info("Mottok en manuell oppgave, {}", fields(loggingMeta))
            INCOMING_MESSAGE_COUNTER.inc()

            if (database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
                log.warn(
                    "Manuell oppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                    manuellOppgave.receivedSykmelding.sykmelding.id, fields(loggingMeta)
                )
            } else {
                try {
                    val oppgaveId = try {
                        oppgaveService.opprettOppgave(manuellOppgave, loggingMeta)
                    } catch (e: Exception) {
                        log.warn("Opprett Oppgave: Kall mot oppgave api feilet: {}, {}", e.message, fields(loggingMeta))
                        throw OpprettOppgaveException("Opprettelse av oppgave feilet ved kall mot oppgave api")
                    }
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
                    log.warn("Noe gikk galt ved oppretting av oppgave: {}, {}", e.message, fields(loggingMeta))
                    throw e
                }
            }
        }
    }
}
