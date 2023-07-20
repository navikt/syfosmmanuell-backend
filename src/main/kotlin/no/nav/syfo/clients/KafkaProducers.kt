package no.nav.syfo.clients

import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.oppgave.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.Properties

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
        val producer = KafkaProducer<String, ReceivedSykmelding>(getKafkaProducerConfig("sykmelding-producer"))
        val okSykmeldingTopic = env.okSykmeldingTopic
    }

    inner class KafkaProduceTaskProducer {
        // Sender til syfosmoppgave
        val producer = KafkaProducer<String, OpprettOppgaveKafkaMessage>(getKafkaProducerConfig("oppgave-producer"))
        val topic = env.produserOppgaveTopic
    }
}
