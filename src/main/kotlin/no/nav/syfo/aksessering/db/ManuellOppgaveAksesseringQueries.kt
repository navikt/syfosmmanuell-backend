package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.objectMapper

fun DatabaseInterface.hentManuellOppgaver(pasientFnr: String): List<ManuellOppgaveDTO> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id,receivedsykmelding,validationresult
                FROM MANUELLOPPGAVE  
                WHERE pasientfnr=?;
                """
        ).use {
            it.setString(1, pasientFnr)
            it.executeQuery().toList { toManuellOppgaveDTO() }
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO =
    ManuellOppgaveDTO(
        id = getString("id").trim(),
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult"))
    )

fun DatabaseInterface.hentKomplettManuellOppgave(manuellOppgaveId: String): List<ManuellOppgave> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT receivedsykmelding,validationresult, apprec
                FROM MANUELLOPPGAVE  
                WHERE id=?;
                """
        ).use {
            it.setString(1, manuellOppgaveId)
            it.executeQuery().toList { toManuellOppgave() }
        }
    }

fun ResultSet.toManuellOppgave(): ManuellOppgave =
    ManuellOppgave(
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult")),
        apprec = objectMapper.readValue(getString("apprec"))
    )
