package no.nav.syfo.aksessering

import java.time.LocalDateTime
import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult

data class ManuellOppgaveDTO(
    val oppgaveid: Int,
    val sykmelding: Sykmelding,
    val personNrPasient: String,
    val mottattDato: LocalDateTime,
    val validationResult: ValidationResult,
)
