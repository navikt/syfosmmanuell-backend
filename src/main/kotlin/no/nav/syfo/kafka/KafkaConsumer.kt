package no.nav.syfo.kafka

import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.logger
import no.nav.syfo.oppgave.service.OppgaveHendelseService
import no.nav.syfo.persistering.MottattSykmeldingService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.ClusterAuthorizationException

class KafkaConsumer(
    private val kafkaAivenConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    private val mottattSykmeldingService: MottattSykmeldingService,
    private val oppgaveHendelseService: OppgaveHendelseService,
    private val oppgaveTopic: String,
    private val manuellOppgaveTopic: String,
    private val cluster: String,
) {
    companion object {
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private const val POLL_TIME_SECONDS = 5L
    }

    @ExperimentalTime
    suspend fun startAivenConsumer() {
        while (applicationState.ready) {
            try {
                runAivenConsumer()
            } catch (ex: Exception) {
                when (ex) {
                    is AuthorizationException,
                    is ClusterAuthorizationException -> {
                        throw ex
                    }
                    else -> {
                        if (cluster == "dev-gcp") {
                            logger.error(
                                "Aiven: Caught exception could not process record skipping in dev",
                                ex
                            )
                        } else {
                            logger.error("Aiven: Caught exception, unsubscribing and retrying", ex)
                            kafkaAivenConsumer.unsubscribe()
                            delay(DELAY_ON_ERROR_SECONDS.seconds)
                        }
                    }
                }
            }
        }
    }

    private suspend fun runAivenConsumer() {
        val topics = listOf(manuellOppgaveTopic, oppgaveTopic)
        kafkaAivenConsumer.subscribe(topics)
        logger.info("Starting consuming topic $topics")
        while (applicationState.ready) {
            kafkaAivenConsumer.poll(Duration.ofSeconds(POLL_TIME_SECONDS)).forEach { consumerRecord
                ->
                when (consumerRecord.topic()) {
                    manuellOppgaveTopic ->
                        mottattSykmeldingService.handleMottattSykmelding(
                            consumerRecord.key(),
                            consumerRecord.value()
                        )
                    oppgaveTopic -> oppgaveHendelseService.handleOppgaveHendelse(consumerRecord)
                    else ->
                        throw IllegalArgumentException(
                            "Topic ${consumerRecord.topic()} is not handled"
                        )
                }
            }
        }
    }
}
