package no.nav.syfo

import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

internal class KafkaITTest {

    val topic = "aapen-test-topic"

    val embeddedEnvironment = KafkaEnvironment(
        autoStart = false,
        topicNames = listOf(topic)
    )

    val credentials = VaultCredentials("", "", "", "")

    val config = Environment(
        kafkaBootstrapServers = embeddedEnvironment.brokersURL,
        applicationName = "syfosminfotrygd",
        mountPathVault = "", databaseName = "", syfosmmanuellbackendDBURL = "url",
        syfosmmanuellUrl = "https://syfosmmanuell", mqHostname = "mqhost", mqPort = 1342,
        mqGatewayName = "mqGateway", mqChannelName = "syfomottak", syfoserviceQueueName = ""
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig
        .toProducerConfig("junit.integration", valueSerializer = StringSerializer::class)
    val producer = KafkaProducer<String, String>(producerProperties)

    val consumerProperties = baseConfig
        .toConsumerConfig("junit.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)

        @BeforeClass
        internal fun beforeClass() {
            embeddedEnvironment.start()
        }

        @AfterClass
        internal fun tearDown() {
            embeddedEnvironment.tearDown()
        }

    @Test
    internal fun `Can read the messages from the kafka topic`() {
        val message = "Test message"
        producer.send(ProducerRecord(topic, message))
        consumer.subscribe(listOf(topic))

        val messages = consumer.poll(Duration.ofMillis(5000)).toList()
        messages.size shouldEqual 1
        messages[0].value() shouldEqual message
    }
}
