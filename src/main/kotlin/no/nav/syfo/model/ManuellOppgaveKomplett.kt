package no.nav.syfo.model

data class ManuellOppgaveKomplett(
    val receivedSykmelding: ReceivedSykmelding,
    val validationResult: ValidationResult,
    val apprec: Apprec,
    val oppgaveid: Int,
    val ferdigstilt: Boolean,
    val sendtApprec: Boolean,
    val opprinneligValidationResult: ValidationResult?
) {
    fun addMerknader(merknadList: List<Merknad>?): ManuellOppgaveKomplett {
        if (merknadList.isNullOrEmpty()) {
            return this
        }
        return this.copy(receivedSykmelding = this.receivedSykmelding.copy(merknader = merknadList))
    }
}
