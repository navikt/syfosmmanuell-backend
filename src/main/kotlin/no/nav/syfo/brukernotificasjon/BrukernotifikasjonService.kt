package no.nav.syfo.brukernotificasjon

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.syfo.brukernotificasjon.db.Brukernotifikasjon
import no.nav.syfo.brukernotificasjon.db.getBrukerNotifikasjon
import no.nav.syfo.brukernotificasjon.db.insertBrukernotifikasjon
import no.nav.syfo.brukernotificasjon.kafka.BeskjedProducer
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgave

class BrukernotifikasjonService(
    private val brukernotifikasjonProducer: BeskjedProducer,
    private val database: DatabaseInterface,
    private val systemBruker: String
) {
    companion object {
        private val BESKJED_TEXT = BrukernotifikasjonService::class.java.getResource("/tekst/manuell_sykmelding_beskjed.txt").readText().trim()
    }

    fun sendBrukerNotifikasjon(manuellOppgaveKomplett: ManuellOppgave) {
        val brukernotifikasjon = database.getBrukerNotifikasjon(manuellOppgaveKomplett.receivedSykmelding.sykmelding.id) ?: createAndInsertBrukernotifikasjon(manuellOppgaveKomplett)

        val nokkel = Nokkel(systemBruker, brukernotifikasjon.eventId)
        val beskjed = Beskjed(brukernotifikasjon.timestamp, Instant.ofEpochMilli(brukernotifikasjon.timestamp).plus(30, ChronoUnit.DAYS).toEpochMilli(), brukernotifikasjon.fnr, brukernotifikasjon.sykmeldingId, BESKJED_TEXT, "", 4)

        brukernotifikasjonProducer.sendBeskjed(nokkel, beskjed)
    }

    private fun createAndInsertBrukernotifikasjon(manuellOppgaveKomplett: ManuellOppgave): Brukernotifikasjon {
        val notifikasjon = Brukernotifikasjon(
            eventId = UUID.randomUUID().toString(),
            sykmeldingId = manuellOppgaveKomplett.receivedSykmelding.sykmelding.id,
            fnr = manuellOppgaveKomplett.receivedSykmelding.personNrPasient,
            timestamp = Instant.now().toEpochMilli()
        )
        database.insertBrukernotifikasjon(notifikasjon)
        return notifikasjon
    }
}
