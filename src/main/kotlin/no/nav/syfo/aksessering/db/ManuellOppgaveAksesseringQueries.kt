package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper

fun DatabaseInterface.hentManuellOppgaver(oppgaveId: Int): ManuellOppgaveDTO? =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT oppgaveid,receivedsykmelding,validationresult
                FROM MANUELLOPPGAVE  
                WHERE oppgaveid=? 
                AND ferdigstilt=?;
                """
        ).use {
            it.setInt(1, oppgaveId)
            it.setBoolean(2, false)
            it.executeQuery().toList { toManuellOppgaveDTO() }.firstOrNull()
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO {
    val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(getString("receivedsykmelding"))
    return ManuellOppgaveDTO(
            oppgaveid = getInt("oppgaveid"),
            sykmelding = receivedSykmelding.sykmelding,
            personNrPasient = receivedSykmelding.personNrPasient,
            mottattDato = receivedSykmelding.mottattDato,
            validationResult = objectMapper.readValue(getString("validationresult"))
    )
}

fun DatabaseInterface.hentKomplettManuellOppgave(oppgaveId: Int): List<ManuellOppgaveKomplett> =
    connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT receivedsykmelding,validationresult,apprec,oppgaveid,ferdigstilt
                FROM MANUELLOPPGAVE  
                WHERE oppgaveid=?;
                """
        ).use {
            it.setInt(1, oppgaveId)
            it.executeQuery().toList { toManuellOppgave() }
        }
    }

fun ResultSet.toManuellOppgave(): ManuellOppgaveKomplett =
    ManuellOppgaveKomplett(
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult")),
        apprec = objectMapper.readValue(getString("apprec")),
        oppgaveid = getInt("oppgaveid"),
        ferdigstilt = getBoolean("ferdigstilt")
    )
