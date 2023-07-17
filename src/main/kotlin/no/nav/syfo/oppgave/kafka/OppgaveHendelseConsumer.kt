package no.nav.syfo.oppgave.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.ClusterAuthorizationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class OppgaveHendelseConsumer(
    private val kafkaConsumer: KafkaConsumer<String, OppgaveKafkaAivenRecord>,
    private val topic: String,
    private val applicationState: ApplicationState,
) {
    companion object {
        private val DELAY_ON_ERROR_SECONDS = 60.seconds
        private val POLL_TIME_DURATION = 10.seconds
    }

    suspend fun start() {
        while (applicationState.ready) {
            try {
                kafkaConsumer.subscribe(listOf(topic))
                consumeMessages()
            } catch (ex: Exception) {
                when (ex) {
                    is AuthorizationException, is ClusterAuthorizationException -> {
                        throw ex
                    }

                    else -> {
                        log.error("Aiven: Caught exception, unsubscribing and retrying", ex)
                        kafkaConsumer.unsubscribe()
                        delay(DELAY_ON_ERROR_SECONDS)
                    }
                }
            }
        }
    }

    suspend fun consumeMessages() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(POLL_TIME_DURATION.toJavaDuration())
            records.forEach {
                val oppgaveHendlese = it.value()
            }
        }
    }
}
