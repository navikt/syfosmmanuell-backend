package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.model.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.oppgave.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig

class KafkaProducers(private val env: Environment) {
    private val producerPropertiesAiven = KafkaUtils.getAivenKafkaConfig()
        .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    init {
        producerPropertiesAiven[ProducerConfig.RETRIES_CONFIG] = 100
    }

    val kafkaApprecProducer = KafkaApprecProducer()
    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer()
    val kafkaSyfoserviceProducer = KafkaSyfoserviceProducer()
    val kafkaProduceTaskProducer = KafkaProduceTaskProducer()

    inner class KafkaApprecProducer {
        val producer = KafkaProducer<String, Apprec>(producerPropertiesAiven)
        val apprecTopic = env.apprecTopic
    }

    inner class KafkaRecievedSykmeldingProducer {
        val producer = KafkaProducer<String, ReceivedSykmelding>(producerPropertiesAiven)
        val okSykmeldingTopic = env.okSykmeldingTopic
    }

    inner class KafkaSyfoserviceProducer {
        val producer = KafkaProducer<String, SyfoserviceSykmeldingKafkaMessage>(producerPropertiesAiven)
        val topic = env.smSyfoserviceMqTopic
    }

    inner class KafkaProduceTaskProducer {
        // Sender til syfosmoppgave
        val producer = KafkaProducer<String, OpprettOppgaveKafkaMessage>(producerPropertiesAiven)
        val topic = env.produserOppgaveTopic
    }
}
