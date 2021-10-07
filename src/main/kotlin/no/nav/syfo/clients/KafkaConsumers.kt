package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaConsumers(env: Environment, vaultSecrets: KafkaCredentials) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)

    init {
        kafkaBaseConfig["auto.offset.reset"] = "none"
    }

    private val properties = kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)

    val kafkaConsumerManuellOppgave = KafkaConsumer<String, String>(properties)
}
