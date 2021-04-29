package no.nav.syfo.brukernotificasjon.db

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import no.nav.syfo.db.DatabaseInterface

fun DatabaseInterface.insertBrukernotifikasjon(brukerNotifikasjon: Brukernotifikasjon) {
    connection.use {
        it.prepareStatement("""
           INSERT INTO brukernotifikasjon(sykmelding_id, event_id, pasient_fnr, timestamp) VALUES (?, ?, ?, ?);
        """).use { ps ->
            ps.setString(1, brukerNotifikasjon.sykmeldingId)
            ps.setString(2, brukerNotifikasjon.eventId)
            ps.setString(3, brukerNotifikasjon.fnr)
            ps.setTimestamp(4, Timestamp.from(Instant.ofEpochMilli(brukerNotifikasjon.timestamp)))
            ps.executeUpdate()
        }
        it.commit()
    }
}

fun DatabaseInterface.getBrukerNotifikasjon(sykmeldingId: String): Brukernotifikasjon? {
    return connection.use {
        it.prepareStatement("""
            select * from brukernotifikasjon where sykmelding_id = ?
        """).use { ps ->
            ps.setString(1, sykmeldingId)
            ps.executeQuery().toBrukernotifikasjon()
        }
    }
}

private fun ResultSet.toBrukernotifikasjon(): Brukernotifikasjon? {
    return when (next()) {
        true -> {
            Brukernotifikasjon(
                eventId = getString("event_id"),
                sykmeldingId = getString("sykmelding_id"),
                fnr = getString("pasient_fnr"),
                timestamp = getTimestamp("timestamp").toInstant().toEpochMilli()
            )
        }
        false -> null
    }
}
