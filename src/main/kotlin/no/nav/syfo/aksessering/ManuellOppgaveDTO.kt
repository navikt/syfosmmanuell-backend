package no.nav.syfo.aksessering

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult

data class ManuellOppgaveDTO(
    val id: String,
    val receivedSykmelding: ReceivedSykmelding,
    val validationResult: ValidationResult
)
