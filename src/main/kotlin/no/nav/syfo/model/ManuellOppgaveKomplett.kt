package no.nav.syfo.model

data class ManuellOppgaveKomplett(
    val receivedSykmelding: ReceivedSykmelding,
    val validationResult: ValidationResult,
    val apprec: Apprec?,
    val oppgaveid: Int,
    val ferdigstilt: Boolean,
    val sendtApprec: Boolean,
    val opprinneligValidationResult: ValidationResult?,
) {
    fun updateMerknader(merknadList: List<Merknad>?): ManuellOppgaveKomplett {
        return this.copy(receivedSykmelding = this.receivedSykmelding.copy(merknader = merknadList))
    }

    fun toManuellOppgave(): ManuellOppgave {
        return ManuellOppgave(
            receivedSykmelding = receivedSykmelding,
            validationResult = validationResult,
            apprec = apprec,
        )
    }
}
