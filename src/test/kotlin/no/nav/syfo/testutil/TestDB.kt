package no.nav.syfo.testutil

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.toPGObject
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

class TestDB private constructor() {

    companion object {
        var database: DatabaseInterface
        val mockEnv = mockk<Environment>(relaxed = true)
        init {
            val postgres = PostgreSQLContainer<Nothing>("postgres:14").apply {
                withCommand("postgres", "-c", "wal_level=logical")
                withUsername("username")
                withPassword("password")
                withDatabaseName("database")
                withInitScript("db/db-init.sql")
                start()
                println("Database: jdbc:postgresql://localhost:$firstMappedPort/test startet opp, credentials: test og test")
            }

            every { mockEnv.databaseUsername } returns postgres.username
            every { mockEnv.databasePassword } returns postgres.password
            every { mockEnv.dbName } returns postgres.databaseName
            every { mockEnv.dbPort } returns postgres.firstMappedPort.toString()
            database = Database(mockEnv)
        }
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
