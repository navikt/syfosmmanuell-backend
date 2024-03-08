package no.nav.syfo.clients

import java.util.Properties
import kotlin.reflect.KClass
import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.oppgave.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

fun Properties.toProducerConfig(
    groupId: String,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer.java
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerializer.java
    }

class KafkaProducers(private val env: Environment) {
    fun getKafkaProducerConfig(clientId: String): Properties {
        return KafkaUtils.getAivenKafkaConfig(clientId)
            .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    }

    val kafkaApprecProducer = KafkaApprecProducer()
    val kafkaRecievedSykmeldingProducer = KafkaRecievedSykmeldingProducer()
    val kafkaProduceTaskProducer = KafkaProduceTaskProducer()

    inner class KafkaApprecProducer {
        val producer = KafkaProducer<String, Apprec>(getKafkaProducerConfig("apprec-producer"))
        val apprecTopic = env.apprecTopic
    }

    inner class KafkaRecievedSykmeldingProducer {
        val producer =
            KafkaProducer<String, ReceivedSykmeldingWithValidation>(
                getKafkaProducerConfig("sykmelding-producer")
            )
        val okSykmeldingTopic = env.okSykmeldingTopic
    }

    inner class KafkaProduceTaskProducer {
        // Sender til syfosmoppgave
        val producer =
            KafkaProducer<String, OpprettOppgaveKafkaMessage>(
                getKafkaProducerConfig("oppgave-producer")
            )
        val topic = env.produserOppgaveTopic
    }
}
