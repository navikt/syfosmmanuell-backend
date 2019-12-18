package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.objectMapper

fun DatabaseInterface.hentManuellOppgaver(oppgaveId: String): List<ManuellOppgaveDTO> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT id,receivedsykmelding,validationresult
                FROM MANUELLOPPGAVE  
                WHERE oppgaveid=? 
                AND ferdigstilt=?;
                """
        ).use {
            it.setString(1, oppgaveId)
            it.setBoolean(2, false)
            it.executeQuery().toList { toManuellOppgaveDTO() }
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO =
    ManuellOppgaveDTO(
        manuellOppgaveid = getString("id").trim(),
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult"))
    )

fun DatabaseInterface.hentKomplettManuellOppgave(manuellOppgaveId: String): List<ManuellOppgaveKomplett> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT receivedsykmelding,validationresult,apprec,tildeltenhetsnr,oppgaveid
                FROM MANUELLOPPGAVE  
                WHERE id=?;
                """
        ).use {
            it.setString(1, manuellOppgaveId)
            it.executeQuery().toList { toManuellOppgave() }
        }
    }

fun ResultSet.toManuellOppgave(): ManuellOppgaveKomplett =
    ManuellOppgaveKomplett(
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult")),
        apprec = objectMapper.readValue(getString("apprec")),
        behandlendeEnhet = getString("tildeltenhetsnr").trim(),
        oppgaveid = getInt("oppgaveid")
    )
