package no.nav.syfo.aksessering

import java.time.LocalDateTime
import no.nav.syfo.model.ManuellOppgaveStatus

data class UlosteOppgave(
    val oppgaveId: Int,
    val mottattDato: LocalDateTime,
    val status: ManuellOppgaveStatus? = null,
)
