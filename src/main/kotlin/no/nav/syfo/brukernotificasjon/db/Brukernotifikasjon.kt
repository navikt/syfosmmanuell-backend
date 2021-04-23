package no.nav.syfo.brukernotificasjon.db

data class Brukernotifikasjon(
    val eventId: String,
    val sykmeldingId: String,
    val fnr: String,
    val timestamp: Long
)
