package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.objectMapper

fun DatabaseInterface.hentManuellOppgave(manuellOppgaveId: String): ManuellOppgaveDTO =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT receivedsykmelding,validationresult
                FROM MANUELLOPPGAVE  
                WHERE id=?;
                """
        ).use {
            it.setString(1, manuellOppgaveId)
            it.executeQuery().toManuellOppgaveDTO()
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO =
    ManuellOppgaveDTO(
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult"))
    )
