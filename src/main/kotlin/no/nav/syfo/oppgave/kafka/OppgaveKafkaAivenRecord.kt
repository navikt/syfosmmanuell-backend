package no.nav.syfo.oppgave.kafka

import java.time.LocalDateTime

data class OppgaveKafkaAivenRecord(
    val hendelse: Hendelse,
    val oppgave: Oppgave,
)

data class Oppgave(
    val oppgaveId: Long,
    val bruker: Bruker?,
    val kategorisering: Kategorisering,
)

data class Bruker(
    val ident: String,
    val identType: IdentType,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    NPID,
    ORGNR,
    SAMHANDLERNR,
}

data class Kategorisering(
    val tema: String,
    val oppgavetype: String,
    val behandlingstema: String?,
    val behandlingstype: String?,
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
