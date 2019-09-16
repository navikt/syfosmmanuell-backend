package no.nav.syfo.model

data class ManuellOppgave(
    val receivedSykmelding: ReceivedSykmelding,
    val validationResult: ValidationResult,
    val apprec: Apprec
)
