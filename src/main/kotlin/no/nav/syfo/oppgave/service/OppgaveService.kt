package no.nav.syfo.oppgave.service

import io.ktor.util.KtorExperimentalAPI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.Veileder
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

    suspend fun opprettOppfoligingsOppgave(manuellOppgave: ManuellOppgaveKomplett, enhet: String, veileder: Veileder, loggingMeta: LoggingMeta): Int {
        val oppfolgingsoppgave = tilOppfolgingsoppgave(manuellOppgave, enhet, veileder)
        val oppgaveResponse = oppgaveClient.opprettOppgave(oppfolgingsoppgave, manuellOppgave.receivedSykmelding.msgId)
        log.info(
                "Opprettet oppfølgingsoppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                StructuredArguments.fields(loggingMeta)
        )
        return oppgaveResponse.id
    }

    suspend fun ferdigstillOppgave(manuellOppgave: ManuellOppgaveKomplett, loggingMeta: LoggingMeta, enhet: String, veileder: Veileder) {
        val oppgave = oppgaveClient.hentOppgave(manuellOppgave.oppgaveid, manuellOppgave.receivedSykmelding.msgId)
        val oppgaveVersjon = oppgave.versjon

        if (oppgave.status != OppgaveStatus.FERDIGSTILT.name) {
            val ferdigstillOppgave = FerdigstillOppgave(
                    versjon = oppgaveVersjon,
                    id = manuellOppgave.oppgaveid,
                    status = OppgaveStatus.FERDIGSTILT,
                    tildeltEnhetsnr = enhet,
                    tilordnetRessurs = veileder.veilederIdent,
                    mappeId = if (oppgave.tildeltEnhetsnr == enhet) {
                        oppgave.mappeId
                    } else {
                        // Det skaper trøbbel i Oppgave-apiet hvis enheten som blir satt ikke har den aktuelle mappen
                        null
                    }
            )

            log.info("Forsøker å ferdigstille oppgave {}, {}", StructuredArguments.fields(ferdigstillOppgave), StructuredArguments.fields(loggingMeta))

            val oppgaveResponse = oppgaveClient.ferdigstillOppgave(ferdigstillOppgave, manuellOppgave.receivedSykmelding.msgId)
            log.info(
                    "Ferdigstilt oppgave med {}, {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                    StructuredArguments.fields(loggingMeta)
            )
        } else {
            log.info("Oppgaven er allerede ferdigstillt oppgaveId: ${oppgave.id} {}", StructuredArguments.fields(loggingMeta))
        }
    }

    fun tilOpprettOppgave(manuellOppgave: ManuellOppgave): OpprettOppgave =
            OpprettOppgave(
                    aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
                    opprettetAvEnhetsnr = "9999",
                    behandlesAvApplikasjon = "SMM",
                    beskrivelse = "Manuell vurdering av sykmelding for periode: ${getFomTomTekst(manuellOppgave.receivedSykmelding)}",
                    tema = "SYM",
                    oppgavetype = "BEH_EL_SYM",
                    behandlingstype = "ae0239",
                    aktivDato = LocalDate.now(),
                    fristFerdigstillelse = omTreUkedager(LocalDate.now()),
                    prioritet = "HOY"
            )

    fun tilOppfolgingsoppgave(manuellOppgave: ManuellOppgaveKomplett, enhet: String, veileder: Veileder): OpprettOppgave =
            OpprettOppgave(
                    aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
                    tildeltEnhetsnr = enhet,
                    opprettetAvEnhetsnr = "9999",
                    tilordnetRessurs = veileder.veilederIdent,
                    behandlesAvApplikasjon = "FS22",
                    beskrivelse = "Oppfølgingsoppgave for sykmelding registrert med merknad " +
                            manuellOppgave.receivedSykmelding.merknader?.joinToString { it.type },
                    tema = "SYM",
                    oppgavetype = "BEH_EL_SYM",
                    aktivDato = LocalDate.now(),
                    prioritet = "HOY"
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
