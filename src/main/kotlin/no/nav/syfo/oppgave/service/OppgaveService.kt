package no.nav.syfo.oppgave.service

import io.ktor.util.KtorExperimentalAPI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.oppgave.FerdigstillOppgave
import no.nav.syfo.oppgave.OppgaveStatus
import no.nav.syfo.oppgave.OpprettOppgave
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class OppgaveService(private val oppgaveClient: OppgaveClient) {

    suspend fun opprettOppgave(manuellOppgave: ManuellOppgave, loggingMeta: LoggingMeta): Int {
        val opprettOppgave = tilOpprettOppgave(manuellOppgave)
        val oppgaveResponse = oppgaveClient.opprettOppgave(opprettOppgave, manuellOppgave.receivedSykmelding.msgId)
        OPPRETT_OPPGAVE_COUNTER.inc()
        log.info(
            "Opprettet manuell sykmeldingsoppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
            StructuredArguments.fields(loggingMeta)
        )
        return oppgaveResponse.id
    }

    suspend fun ferdigstillOppgave(manuellOppgave: ManuellOppgaveKomplett, loggingMeta: LoggingMeta) {
        val oppgaveVersjon = oppgaveClient.hentOppgave(manuellOppgave.oppgaveid, manuellOppgave.receivedSykmelding.msgId).versjon
        val ferdigstillOppgave = ferdigstillOppgave(manuellOppgave, oppgaveVersjon)
        val oppgaveResponse = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, manuellOppgave.receivedSykmelding.msgId)

        log.info(
            "Ferdigstiller oppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
            StructuredArguments.fields(loggingMeta)
        )
    }

    fun tilOpprettOppgave(manuellOppgave: ManuellOppgave): OpprettOppgave =
        OpprettOppgave(
            aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
            opprettetAvEnhetsnr = "9999",
            behandlesAvApplikasjon = "FS22",
            beskrivelse = "Manuell vurdering av sykmelding for periode: ${getFomTomTekst(manuellOppgave.receivedSykmelding)}",
            tema = "SYM",
            oppgavetype = "BEH_EL_SYM",
            behandlingstype = "ae0239",
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = omTreUkedager(LocalDate.now()),
            prioritet = "HOY"
        )

    private fun ferdigstillOppgave(manuellOppgave: ManuellOppgaveKomplett, oppgaveVersjon: Int) = FerdigstillOppgave(
        versjon = oppgaveVersjon,
        id = manuellOppgave.oppgaveid,
        status = OppgaveStatus.FERDIGSTILT
    )

    fun omTreUkedager(idag: LocalDate): LocalDate = when (idag.dayOfWeek) {
        DayOfWeek.SUNDAY -> idag.plusDays(4)
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> idag.plusDays(3)
        else -> idag.plusDays(5)
    }

    private fun getFomTomTekst(receivedSykmelding: ReceivedSykmelding) =
        "${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
            " ${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

    private fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
        sortedBy { it.fom }

    private fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
        sortedBy { it.tom }

    private fun formaterDato(dato: LocalDate): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return dato.format(formatter)
    }
}
