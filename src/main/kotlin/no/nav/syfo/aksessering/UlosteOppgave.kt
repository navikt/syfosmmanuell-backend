package no.nav.syfo.aksessering

import no.nav.syfo.model.ManuellOppgaveStatus
import java.time.LocalDateTime

data class UlosteOppgave(
    val oppgaveId: Int,
    val mottattDato: LocalDateTime,
    val status: ManuellOppgaveStatus? = null,
)
