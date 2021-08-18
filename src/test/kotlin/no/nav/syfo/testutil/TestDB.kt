package no.nav.syfo.testutil

import com.opentable.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.toPGObject
import org.flywaydb.core.Flyway

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()
        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM manuelloppgave").executeUpdate()
        connection.prepareStatement("DELETE FROM Brukernotifikasjon").executeUpdate()
        connection.commit()
    }
}

fun Connection.opprettManuellOppgaveUtenOpprinneligValidationResult(manuellOppgaveKomplett: ManuellOppgaveKomplett) {
    use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO MANUELLOPPGAVE(
                id,
                receivedsykmelding,
                validationresult,
                apprec,
                pasientfnr,
                ferdigstilt,
                oppgaveid,
                sendt_apprec,
                opprinnelig_validationresult
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).use {
            it.setString(1, manuellOppgaveKomplett.receivedSykmelding.sykmelding.id)
            it.setObject(2, manuellOppgaveKomplett.receivedSykmelding.toPGObject())
            it.setObject(3, manuellOppgaveKomplett.validationResult.toPGObject())
            it.setObject(4, manuellOppgaveKomplett.apprec.toPGObject())
            it.setString(5, manuellOppgaveKomplett.receivedSykmelding.personNrPasient)
            it.setBoolean(6, false)
            it.setInt(7, manuellOppgaveKomplett.oppgaveid)
            it.setBoolean(8, false)
            it.setObject(9, manuellOppgaveKomplett.opprinneligValidationResult?.toPGObject())
            it.executeUpdate()
        }
        connection.commit()
    }
}
