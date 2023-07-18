package no.nav.syfo.clients

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.oppgave.kafka.OppgaveKafkaAivenRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaOppgaveDeserializer : Deserializer<OppgaveKafkaAivenRecord> {
    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    override fun deserialize(topic: String, bytes: ByteArray): OppgaveKafkaAivenRecord {
        return objectMapper.readValue(bytes, OppgaveKafkaAivenRecord::class.java)
    }
}
class KafkaConsumers(env: Environment) {
    val kafkaAivenConsumerManuellOppgave = KafkaConsumer<String, String>(
        KafkaUtils.getAivenKafkaConfig().also {
            it.let {
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
            }
        }.toConsumerConfig(
            "${env.applicationName}-consumer",
            valueDeserializer = StringDeserializer::class,
        ),
    )
    val oppgaveHendelseConsumer = KafkaConsumer(
        KafkaUtils.getAivenKafkaConfig()
            .also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 50
            }
            .toConsumerConfig(
                "${env.applicationName}-consumer",
                KafkaOppgaveDeserializer::class,
            ),
        StringDeserializer(),
        KafkaOppgaveDeserializer(),
    )
}
