package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaConsumers(env: Environment) {
    private val consumerPropertiesAiven = KafkaUtils.getAivenKafkaConfig().also {
        it.let {
            it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
        }
    }.toConsumerConfig(
        "${env.applicationName}-consumer",
        valueDeserializer = StringDeserializer::class
    )

    val kafkaAivenConsumerManuellOppgave = KafkaConsumer<String, String>(consumerPropertiesAiven)
}
