package no.nav.syfo.clients

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.model.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
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
    val kafkaValidationResultProducer = KafkaValidationResultProducer()
    val kafkaSyfoserviceProducer = KafkaSyfoserviceProducer()
    val kafkaBrukernotifikasjonProducer = BrukernotifikasjonProducer()

    inner class KafkaApprecProducer() {
        val producer = KafkaProducer<String, Apprec>(properties)
        val sm2013ApprecTopic = env.sm2013Apprec
    }

    inner class KafkaRecievedSykmeldingProducer() {
        val producer = KafkaProducer<String, ReceivedSykmelding>(properties)
        val sm2013AutomaticHandlingTopic = env.sm2013AutomaticHandlingTopic
        val sm2013InvalidHandlingTopic = env.sm2013InvalidHandlingTopic
    }

    inner class KafkaValidationResultProducer() {
        val producer = KafkaProducer<String, ValidationResult>(properties)
        val sm2013BehandlingsUtfallTopic = env.sm2013BehandlingsUtfallTopic
    }

    inner class KafkaSyfoserviceProducer() {
        val producer = KafkaProducer<String, SyfoserviceSykmeldingKafkaMessage>(properties)
        val topic = env.smSyfoserviceMqTopic
    }

    inner class BrukernotifikasjonProducer() {
        val producer = KafkaProducer<Nokkel, Beskjed>(kafkaBaseConfig.toProducerConfig(
            groupId = env.applicationName,
            valueSerializer = KafkaAvroSerializer::class, keySerializer = KafkaAvroSerializer::class
        ))
        val topic = env.brukernotifikasjonBeskjedTopic
    }
}
