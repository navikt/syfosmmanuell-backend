package no.nav.syfo.persistering

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toPGObject

fun DatabaseInterface.opprettManuellOppgave(manuellOppgave: ManuellOppgave) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            INSERT INTO MANUELLOPPGAVE(
                id,
                receivedsykmelding,
                validationresult,
                apprec)
            VALUES  (?, ?, ?, ?)
            """
        ).use {
            it.setString(1, manuellOppgave.receivedSykmelding.sykmelding.id)
            it.setObject(2, manuellOppgave.receivedSykmelding.toPGObject())
            it.setObject(3, manuellOppgave.validationResult.toPGObject())
            it.setObject(4, manuellOppgave.apprec.toPGObject())
            it.executeUpdate()
        }

        connection.commit()
    }
}

fun DatabaseInterface.erOpprettManuellOppgave(manueloppgaveId: String) =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM MANUELLOPPGAVE
                WHERE id=?;
                """
        ).use {
            it.setString(1, manueloppgaveId)
            it.executeQuery().next()
        }
    }

fun DatabaseInterface.oppdaterValidationResults(manueloppgaveId: String, validationResult: ValidationResult): Int =
    connection.use { connection ->
        val status = connection.prepareStatement(
            """
            UPDATE MANUELLOPPGAVE
            SET validationResult = ?
            WHERE id = ?;
            """
        ).use {
            it.setObject(1, validationResult.toPGObject())
            it.setString(2, manueloppgaveId)
            it.executeUpdate()
        }
        connection.commit()
        return status
    }
