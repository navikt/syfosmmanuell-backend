package no.nav.syfo.oppgave

import java.time.LocalDate

data class OpprettOppgave(
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String
)

data class FerdigstillOppgave(
    val versjon: Int,
    val id: Int,
    val status: OppgaveStatus,
    val tildeltEnhetsnr: String,
    val tilordnetRessurs: String,
    val mappeId: Int?
)

data class OpprettOppgaveResponse(
    val id: Int,
    val versjon: Int,
    val status: String? = null,
    val tildeltEnhetsnr: String? = null,
    val mappeId: Int? = null
)

enum class OppgaveStatus(val status: String) {
    OPPRETTET("OPPRETTET"),
    AAPNET("AAPNET"),
    UNDER_BEHANDLING("UNDER_BEHANDLING"),
    FERDIGSTILT("FERDIGSTILT"),
    FEILREGISTRERT("FEILREGISTRERT")
}
