package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.KafkaConsumer

class KafkaConsumers(env: Environment, vaultSecrets: KafkaCredentials) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    private val properties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaConsumerManuellOppgave = KafkaConsumer<String, String>(properties)
}
