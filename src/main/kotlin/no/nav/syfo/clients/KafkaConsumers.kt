package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.util.setSecurityProtocol
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaConsumers(env: Environment, vaultSecrets: KafkaCredentials) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    private val properties =  setSecurityProtocol(env, kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class))

    val kafkaConsumerManuellOppgave = KafkaConsumer<String, String>(properties)
}
