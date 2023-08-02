package no.nav.syfo.persistering.db

import java.sql.Timestamp
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPGObject

suspend fun DatabaseInterface.opprettManuellOppgave(
    manuellOppgave: ManuellOppgave,
    apprec: Apprec,
    oppgaveId: Int,
    status: ManuellOppgaveStatus,
    statusTimestamp: LocalDateTime,
) {
    withContext(Dispatchers.IO) {
        connection.use { connection ->
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
                opprinnelig_validationresult,
                status,
                status_timestamp
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                )
                .use {
                    it.setString(1, manuellOppgave.receivedSykmelding.sykmelding.id)
                    it.setObject(2, manuellOppgave.receivedSykmelding.toPGObject())
                    it.setObject(3, manuellOppgave.validationResult.toPGObject())
                    it.setObject(4, apprec.toPGObject())
                    it.setString(5, manuellOppgave.receivedSykmelding.personNrPasient)
                    it.setBoolean(6, false)
                    it.setInt(7, oppgaveId)
                    it.setBoolean(8, false)
                    it.setObject(9, manuellOppgave.validationResult.toPGObject())
                    it.setString(10, status.name)
                    it.setTimestamp(11, Timestamp.valueOf(statusTimestamp))
                    it.executeUpdate()
                }
            connection.commit()
        }
    }
}

suspend fun DatabaseInterface.erOpprettManuellOppgave(sykmledingsId: String) =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
                SELECT true
                FROM MANUELLOPPGAVE
                WHERE id=?;
                """,
                )
                .use {
                    it.setString(1, sykmledingsId)
                    it.executeQuery().next()
                }
        }
    }

suspend fun DatabaseInterface.oppdaterManuellOppgave(
    oppgaveId: Int,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult
): Int =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            val status =
                connection
                    .prepareStatement(
                        """
            UPDATE MANUELLOPPGAVE
            SET ferdigstilt = ?,
                receivedsykmelding = ?,
                validationresult = ?
            WHERE oppgaveid = ?;
            """,
                    )
                    .use {
                        it.setBoolean(1, true)
                        it.setObject(2, receivedSykmelding.toPGObject())
                        it.setObject(3, validationResult.toPGObject())
                        it.setInt(4, oppgaveId)
                        it.executeUpdate()
                    }
            connection.commit()
            status
        }
    }

suspend fun DatabaseInterface.oppdaterManuellOppgaveUtenOpprinneligValidationResult(
    oppgaveId: Int,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    opprinneligValidationResult: ValidationResult,
): Int =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            val status =
                connection
                    .prepareStatement(
                        """
            UPDATE MANUELLOPPGAVE
            SET ferdigstilt = ?,
                receivedsykmelding = ?,
                validationresult = ?,
                opprinnelig_validationresult = ?
            WHERE oppgaveid = ?;
            """,
                    )
                    .use {
                        it.setBoolean(1, true)
                        it.setObject(2, receivedSykmelding.toPGObject())
                        it.setObject(3, validationResult.toPGObject())
                        it.setObject(4, opprinneligValidationResult.toPGObject())
                        it.setInt(5, oppgaveId)
                        it.executeUpdate()
                    }
            connection.commit()
            status
        }
    }

suspend fun DatabaseInterface.oppdaterApprecStatus(oppgaveId: Int, sendtApprec: Boolean): Int =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            val status =
                connection
                    .prepareStatement(
                        """
            UPDATE MANUELLOPPGAVE
            SET sendt_apprec = ?
            WHERE oppgaveid = ?;
            """,
                    )
                    .use {
                        it.setBoolean(1, sendtApprec)
                        it.setInt(2, oppgaveId)
                        it.executeUpdate()
                    }
            connection.commit()
            status
        }
    }

suspend fun DatabaseInterface.slettOppgave(oppgaveId: Int): Int =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            val status =
                connection
                    .prepareStatement(
                        """
            DELETE FROM MANUELLOPPGAVE
            WHERE oppgaveid = ?;
            """,
                    )
                    .use {
                        it.setInt(1, oppgaveId)
                        it.executeUpdate()
                    }
            connection.commit()
            status
        }
    }

suspend fun DatabaseInterface.oppdaterOppgaveHendelse(
    oppgaveId: Int,
    status: ManuellOppgaveStatus,
    statusTimestamp: LocalDateTime
) {
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
            UPDATE MANUELLOPPGAVE
            set status = ?,
            status_timestamp = ?
            WHERE oppgaveid = ?;
        """,
                )
                .use {
                    it.setString(1, status.name)
                    it.setTimestamp(2, Timestamp.valueOf(statusTimestamp))
                    it.setInt(3, oppgaveId)
                    it.executeUpdate()
                }
            connection.commit()
        }
    }
}

suspend fun DatabaseInterface.getOppgaveWithNullStatus(limit: Int): List<Pair<Int, String>> =
    withContext(Dispatchers.IO) {
        connection.use { conn ->
            conn
                .prepareStatement(
                    """
                SELECT oppgaveId, id
                FROM MANUELLOPPGAVE
                WHERE status IS NULL
                LIMIT ?;
                """,
                )
                .use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        generateSequence {
                                if (rs.next()) {
                                    rs.getInt("oppgaveId") to rs.getString("id")
                                } else {
                                    null
                                }
                            }
                            .toList()
                    }
                }
        }
    }
