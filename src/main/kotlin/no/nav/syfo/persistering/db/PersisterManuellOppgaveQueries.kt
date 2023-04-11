package no.nav.syfo.persistering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPGObject

fun DatabaseInterface.opprettManuellOppgave(manuellOppgave: ManuellOppgave, apprec: Apprec, oppgaveId: Int) {
    connection.use { connection ->
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
            """,
        ).use {
            it.setString(1, manuellOppgave.receivedSykmelding.sykmelding.id)
            it.setObject(2, manuellOppgave.receivedSykmelding.toPGObject())
            it.setObject(3, manuellOppgave.validationResult.toPGObject())
            it.setObject(4, apprec.toPGObject())
            it.setString(5, manuellOppgave.receivedSykmelding.personNrPasient)
            it.setBoolean(6, false)
            it.setInt(7, oppgaveId)
            it.setBoolean(8, false)
            it.setObject(9, manuellOppgave.validationResult.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.erOpprettManuellOppgave(sykmledingsId: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT true
                FROM MANUELLOPPGAVE
                WHERE id=?;
                """,
        ).use {
            it.setString(1, sykmledingsId)
            it.executeQuery().next()
        }
    }

fun DatabaseInterface.oppdaterManuellOppgave(oppgaveId: Int, receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET ferdigstilt = ?,
                receivedsykmelding = ?,
                validationresult = ?
            WHERE oppgaveid = ?;
            """,
        ).use {
            it.setBoolean(1, true)
            it.setObject(2, receivedSykmelding.toPGObject())
            it.setObject(3, validationResult.toPGObject())
            it.setInt(4, oppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }

fun DatabaseInterface.oppdaterManuellOppgaveUtenOpprinneligValidationResult(
    oppgaveId: Int,
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    opprinneligValidationResult: ValidationResult,
): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET ferdigstilt = ?,
                receivedsykmelding = ?,
                validationresult = ?,
                opprinnelig_validationresult = ?
            WHERE oppgaveid = ?;
            """,
        ).use {
            it.setBoolean(1, true)
            it.setObject(2, receivedSykmelding.toPGObject())
            it.setObject(3, validationResult.toPGObject())
            it.setObject(4, opprinneligValidationResult.toPGObject())
            it.setInt(5, oppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }

fun DatabaseInterface.oppdaterApprecStatus(oppgaveId: Int, sendtApprec: Boolean): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET sendt_apprec = ?
            WHERE oppgaveid = ?;
            """,
        ).use {
            it.setBoolean(1, sendtApprec)
            it.setInt(2, oppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }

fun DatabaseInterface.slettOppgave(oppgaveId: Int): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            DELETE FROM MANUELLOPPGAVE
            WHERE oppgaveid = ?;
            """,
        ).use {
            it.setInt(1, oppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }
