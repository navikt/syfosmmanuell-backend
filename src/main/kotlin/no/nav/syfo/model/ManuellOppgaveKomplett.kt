package no.nav.syfo.model

data class ManuellOppgaveKomplett(
    val receivedSykmelding: ReceivedSykmelding,
    val validationResult: ValidationResult,
    val apprec: Apprec,
    val oppgaveid: Int
)
