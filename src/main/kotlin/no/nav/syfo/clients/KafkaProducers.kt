package no.nav.syfo.clients

import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaProducers (env: Environment, vaultSecrets: VaultSecrets) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    private val properties =
            kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaApprecProducer = KafkaApprecProducer(env, properties)
    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer(env, properties)
    val kafkaValidationResultProducer = KafkaValidationResultProducer(env, properties)

    companion object {
        class KafkaApprecProducer (env: Environment, properties: Properties) {
            val producer = KafkaProducer<String, Apprec>(properties)

            val sm2013ApprecTopic = env.sm2013Apprec
        }

        class KafkaRecievedSykmeldingProducer (env: Environment, properties: Properties) {
            val producer = KafkaProducer<String, ReceivedSykmelding>(properties)

            val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic
            val sm2013InvalidHandlingTopic = env.sm2013InvalidHandlingTopic
            val sm2013BehandlingsUtfallTopic = env.sm2013BehandlingsUtfallTopic
        }

        class KafkaValidationResultProducer (env: Environment, properties: Properties) {
            val producer = KafkaProducer<String, ValidationResult>(properties)

            val syfoserviceQueueName = env.syfoserviceQueueName
        }
    }
}
