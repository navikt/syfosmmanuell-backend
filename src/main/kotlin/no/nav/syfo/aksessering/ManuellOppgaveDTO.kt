package no.nav.syfo.aksessering

import no.nav.syfo.model.Sykmelding
import no.nav.syfo.model.ValidationResult

data class ManuellOppgaveDTO(
    val oppgaveid: Int,
    val sykmelding: Sykmelding,
    val personNrPasient: String,
    val mottattDato: java.time.LocalDateTime,
    val validationResult: ValidationResult
)
