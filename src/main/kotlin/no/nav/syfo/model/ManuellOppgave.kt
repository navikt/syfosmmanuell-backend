package no.nav.syfo.model

import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

data class ManuellOppgave(
    val receivedSykmelding: ReceivedSykmelding,
    val validationResult: ValidationResult,
    val apprec: Apprec?,
)

fun ReceivedSykmelding.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }

fun ValidationResult.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }

fun Apprec.toPGObject() =
    PGobject().also {
        it.type = "json"
        it.value = objectMapper.writeValueAsString(this)
    }

enum class ManuellOppgaveStatus {
    APEN,
    FERDIGSTILT,
    FEILREGISTRERT,
    DELETED,
}
