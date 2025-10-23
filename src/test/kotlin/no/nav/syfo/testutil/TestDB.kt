package no.nav.syfo.testutil

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.toPGObject
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:14")

class TestDatabase(
    private val connectionName: String,
    private val dbUsername: String,
    private val dbPassword: String
) : DatabaseInterface {
    private val dataSource: HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = connectionName
                username = dbUsername
                password = dbPassword
                maximumPoolSize = 1
                minimumIdle = 1
                isAutoCommit = false
                connectionTimeout = 10_000
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            },
        )
    override val connection: Connection
        get() = dataSource.connection

    init {
        runFlywayMigrations()
    }

    private fun runFlywayMigrations() =
        Flyway.configure().run {
            locations("db")
            configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
            dataSource(connectionName, dbUsername, dbPassword)
            load().migrate()
        }
}

class TestDB private constructor() {

    companion object {
        val database: DatabaseInterface

        private val psqlContainer: PsqlContainer

        init {
            try {
                psqlContainer =
                    PsqlContainer()
                        .withCommand("postgres", "-c", "wal_level=logical")
                        .withExposedPorts(5432)
                        .withUsername("username")
                        .withPassword("password")
                        .withDatabaseName("syfosmmanuell-backend")
                        .withInitScript("db/db-init.sql")

                psqlContainer.waitingFor(HostPortWaitStrategy())
                psqlContainer.start()
                val username = "username"
                val password = "password"
                val connectionName = psqlContainer.jdbcUrl

                database = TestDatabase(connectionName, username, password)
            } catch (ex: Exception) {
                logger.error("Error", ex)
                throw ex
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

fun Connection.opprettManuellOppgaveUtenOpprinneligValidationResult(
    manuellOppgaveKomplett: ManuellOppgaveKomplett
) {
    use { connection ->
        connection
            .prepareStatement(
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
            """,
            )
            .use {
                it.setString(1, manuellOppgaveKomplett.receivedSykmelding.sykmelding.id)
                it.setObject(2, manuellOppgaveKomplett.receivedSykmelding.toPGObject())
                it.setObject(3, manuellOppgaveKomplett.validationResult.toPGObject())
                it.setObject(4, manuellOppgaveKomplett.apprec?.toPGObject())
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
