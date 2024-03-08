package no.nav.syfo.clients

import java.util.Properties
import kotlin.reflect.KClass
import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer

fun Properties.toConsumerConfig(
    groupId: String,
    valueDeserializer: KClass<out Deserializer<out Any>>,
    keyDeserializer: KClass<out Deserializer<out Any>> = StringDeserializer::class
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = keyDeserializer.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = valueDeserializer.java
    }

class KafkaConsumers(env: Environment) {
    val kafkaAivenConsumerManuellOppgave =
        KafkaConsumer<String, String>(
            KafkaUtils.getAivenKafkaConfig("manuell-oppgave-consumer")
                .also {
                    it.let {
                        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    }
                }
                .toConsumerConfig(
                    "${env.applicationName}-consumer",
                    valueDeserializer = StringDeserializer::class,
                ),
        )
}
