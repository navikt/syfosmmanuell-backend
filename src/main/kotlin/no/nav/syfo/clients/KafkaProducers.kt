package no.nav.syfo.clients

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.model.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.setSecurityProtocol
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig

class KafkaProducers(private val env: Environment, vaultSecrets: VaultSecrets) {
    private val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets)
    private val properties = setSecurityProtocol(env, kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class))

    init {
        properties[ProducerConfig.RETRIES_CONFIG] = 100
    }

    val kafkaApprecProducer = KafkaApprecProducer()
    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer()
    val kafkaSyfoserviceProducer = KafkaSyfoserviceProducer()
    val kafkaProduceTaskProducer = KafkaProduceTaskProducer()

    inner class KafkaApprecProducer() {
        val producer = KafkaProducer<String, Apprec>(properties)
        val sm2013ApprecTopic = env.sm2013Apprec
    }

    inner class KafkaRecievedSykmeldingProducer() {
        val producer = KafkaProducer<String, ReceivedSykmelding>(properties)
        val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic
    }

    inner class KafkaSyfoserviceProducer() {
        val producer = KafkaProducer<String, SyfoserviceSykmeldingKafkaMessage>(properties)
        val topic = env.smSyfoserviceMqTopic
    }

    inner class KafkaProduceTaskProducer() {
        // Sender til syfosmoppgave
        private val properties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)
        val producer = KafkaProducer<String, ProduceTask>(properties)
        val topic = env.sm2013OpppgaveTopic
    }
}
