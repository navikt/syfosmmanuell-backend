package no.nav.syfo.testutil

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.db.VaultCredentials
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.toPGObject
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDB private constructor() {

    companion object {
        var database: DatabaseInterface
        val vaultCredentialService = mockk<VaultCredentialService>()
        val env = mockk<Environment>()
        val psqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("user")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/db-init.sql")

        init {
            psqlContainer.start()
            every { env.databaseName } returns "database"
            every { env.mountPathVault } returns ""
            every { env.syfosmmanuellbackendDBURL } returns psqlContainer.jdbcUrl
            every { vaultCredentialService.renewCredentialsTaskData = any() } returns Unit
            every { vaultCredentialService.getNewCredentials(any(), any(), any()) } returns VaultCredentials(
                "1",
                "user",
                "password"
            )
            try {
                database = Database(env, vaultCredentialService)
            } catch (ex: Exception) {
                log.error("error", ex)
                database = Database(env, vaultCredentialService)
            }
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
