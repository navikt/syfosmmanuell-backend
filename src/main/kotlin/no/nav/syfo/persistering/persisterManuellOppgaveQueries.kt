package no.nav.syfo.persistering

import java.sql.Connection
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.toPGObject

fun Connection.opprettManuellOppgave(manuellOppgave: ManuellOppgave) {
    use { connection ->
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

fun Connection.erOpprettManuellOppgave(sykmeldingsid: String) =
    use { connection ->
        connection.prepareStatement(
            """
                SELECT *
                FROM MANUELLOPPGAVE
                WHERE id=?;
                """
        ).use {
            it.setString(1, sykmeldingsid)
            it.executeQuery().next()
        }
    }
