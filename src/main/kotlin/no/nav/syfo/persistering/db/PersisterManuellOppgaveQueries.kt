package no.nav.syfo.persistering.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPGObject

fun DatabaseInterface.opprettManuellOppgave(manuellOppgave: ManuellOppgave, oppgaveId: Int) {
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
                oppgaveid
                )
            VALUES  (?, ?, ?, ?, ?, ?, ?)
            """
        ).use {
            it.setString(1, manuellOppgave.receivedSykmelding.sykmelding.id)
            it.setObject(2, manuellOppgave.receivedSykmelding.toPGObject())
            it.setObject(3, manuellOppgave.validationResult.toPGObject())
            it.setObject(4, manuellOppgave.apprec.toPGObject())
            it.setString(5, manuellOppgave.receivedSykmelding.personNrPasient)
            it.setBoolean(6, false)
            it.setInt(7, oppgaveId)
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.erOpprettManuellOppgave(sykmledingsId: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM MANUELLOPPGAVE
                WHERE id=?;
                """
        ).use {
            it.setString(1, sykmledingsId)
            it.executeQuery().next()
        }
    }

fun DatabaseInterface.oppdaterValidationResultsOgApprec(oppgaveId: Int, validationResult: ValidationResult, apprec: Apprec): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET validationResult = ?, ferdigstilt = ?, apprec = ?
            WHERE oppgaveid = ?;
            """
        ).use {
            it.setObject(1, validationResult.toPGObject())
            it.setBoolean(2, true)
            it.setObject(3, apprec.toPGObject())
            it.setInt(4, oppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }
