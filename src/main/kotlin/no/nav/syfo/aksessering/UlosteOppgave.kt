package no.nav.syfo.aksessering

import java.time.LocalDateTime

data class UlosteOppgave(
    val oppgaveId: Int,
    val mottattDato: LocalDateTime
)
