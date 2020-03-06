package no.nav.syfo.persistering

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.finnFristForFerdigstillingAvOppgave
import no.nav.syfo.db.Database
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MESSAGE_STORED_IN_DB_COUNTER
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.wrapExceptions

@KtorExperimentalAPI
suspend fun handleRecivedMessage(
    manuellOppgave: ManuellOppgave,
    loggingMeta: LoggingMeta,
    database: Database,
    oppgaveClient: OppgaveClient
) {
    wrapExceptions(loggingMeta) {
        log.info("Mottok ein manuell oppgave, {}", fields(loggingMeta))
        INCOMING_MESSAGE_COUNTER.inc()

        if (database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id)) {
            log.warn(
                "Manuell oppgave med sykmeldingsid {}, er allerede lagret i databasen, {}",
                manuellOppgave.receivedSykmelding.sykmelding.id, fields(loggingMeta)
            )
        } else {
            val opprettOppgave = OpprettOppgave(
                aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
                opprettetAvEnhetsnr = "9999",
                behandlesAvApplikasjon = "FS22",
                beskrivelse = "Manuell sykmeldingoppgave, gjelder for Sykmelding: ${getFomTomTekst(manuellOppgave.receivedSykmelding)}",
                tema = "SYM",
                oppgavetype = "BEH_EL_SYM",
                behandlingstype = "ae0239",
                aktivDato = LocalDate.now(),
                fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now()),
                prioritet = "HOY"
            )

            val oppgaveResponse = oppgaveClient.opprettOppgave(opprettOppgave, manuellOppgave.receivedSykmelding.msgId)
            OPPRETT_OPPGAVE_COUNTER.inc()
            log.info(
                "Opprettet manuell sykmeldings oppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                fields(loggingMeta)
            )

            database.opprettManuellOppgave(manuellOppgave, oppgaveResponse.id)
            log.info(
                "Manuell oppgave lagret i databasen, for {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                fields(loggingMeta)
            )
            MESSAGE_STORED_IN_DB_COUNTER.inc()
        }
    }
}

private fun getFomTomTekst(receivedSykmelding: ReceivedSykmelding) =
    "${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
            " ${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> =
    sortedBy { it.fom }

fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> =
    sortedBy { it.tom }

fun formaterDato(dato: LocalDate): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}
