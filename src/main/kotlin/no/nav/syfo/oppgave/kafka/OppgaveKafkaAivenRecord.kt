package no.nav.syfo.oppgave.kafka

import java.time.LocalDateTime
import no.nav.syfo.model.ManuellOppgaveStatus

data class OppgaveKafkaAivenRecord(
    val hendelse: Hendelse,
    val oppgave: Oppgave,
)

data class Oppgave(
    val oppgaveId: Long,
)

data class Hendelse(
    val hendelsestype: Hendelsestype,
    val tidspunkt: LocalDateTime,
)

enum class Hendelsestype {
    OPPGAVE_OPPRETTET,
    OPPGAVE_ENDRET,
    OPPGAVE_FERDIGSTILT,
    OPPGAVE_FEILREGISTRERT,
}

fun Hendelsestype.manuellOppgaveStatus(): ManuellOppgaveStatus {
    return when (this) {
        Hendelsestype.OPPGAVE_OPPRETTET -> ManuellOppgaveStatus.APEN
        Hendelsestype.OPPGAVE_ENDRET -> ManuellOppgaveStatus.APEN
        Hendelsestype.OPPGAVE_FERDIGSTILT -> ManuellOppgaveStatus.FERDIGSTILT
        Hendelsestype.OPPGAVE_FEILREGISTRERT -> ManuellOppgaveStatus.FEILREGISTRERT
    }
}
