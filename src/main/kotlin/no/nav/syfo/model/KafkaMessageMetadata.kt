package no.nav.syfo.kafka.model

data class KafkaMessageMetadata(
    val sykmeldingId: String,
    val source: String
)
