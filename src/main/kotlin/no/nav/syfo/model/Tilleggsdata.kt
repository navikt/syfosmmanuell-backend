package no.nav.syfo.model

import java.time.LocalDateTime

data class Tilleggsdata(
    val ediLoggId: String,
    val sykmeldingId: String,
    val msgId: String,
    val syketilfelleStartDato: LocalDateTime
)
