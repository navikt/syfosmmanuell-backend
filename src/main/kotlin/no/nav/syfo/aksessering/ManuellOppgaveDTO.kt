package no.nav.syfo.aksessering

import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult
import java.time.LocalDateTime

data class ManuellOppgaveDTO(
    val oppgaveid: Int,
    val sykmelding: Sykmelding,
    val personNrPasient: String,
    val mottattDato: LocalDateTime,
    val validationResult: ValidationResult,
)
