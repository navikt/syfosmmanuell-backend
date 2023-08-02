package no.nav.syfo.aksessering.db

import com.fasterxml.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.UlosteOppgave
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper

suspend fun DatabaseInterface.finnesOppgave(oppgaveId: Int) =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
                SELECT true
                FROM MANUELLOPPGAVE
                WHERE oppgaveid=?;
                """,
                )
                .use {
                    it.setInt(1, oppgaveId)
                    it.executeQuery().next()
                }
        }
    }

suspend fun DatabaseInterface.finnesSykmelding(id: String) =
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
                    it.setString(1, id)
                    it.executeQuery().next()
                }
        }
    }

suspend fun DatabaseInterface.erApprecSendt(oppgaveId: Int) =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
                SELECT true
                FROM MANUELLOPPGAVE
                WHERE oppgaveid=?
                AND sendt_apprec=?;
                """,
                )
                .use {
                    it.setInt(1, oppgaveId)
                    it.setBoolean(2, true)
                    it.executeQuery().next()
                }
        }
    }

suspend fun DatabaseInterface.hentManuellOppgaver(oppgaveId: Int): ManuellOppgaveDTO? =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
                SELECT oppgaveid,receivedsykmelding,validationresult
                FROM MANUELLOPPGAVE  
                WHERE oppgaveid=? 
                AND ferdigstilt=?;
                """,
                )
                .use {
                    it.setInt(1, oppgaveId)
                    it.setBoolean(2, false)
                    it.executeQuery().toList { toManuellOppgaveDTO() }.firstOrNull()
                }
        }
    }

fun ResultSet.toManuellOppgaveDTO(): ManuellOppgaveDTO {
    val receivedSykmelding: ReceivedSykmelding =
        objectMapper.readValue(getString("receivedsykmelding"))
    return ManuellOppgaveDTO(
        oppgaveid = getInt("oppgaveid"),
        sykmelding = receivedSykmelding.sykmelding,
        personNrPasient = receivedSykmelding.personNrPasient,
        mottattDato = receivedSykmelding.mottattDato,
        validationResult = objectMapper.readValue(getString("validationresult")),
    )
}

suspend fun DatabaseInterface.hentKomplettManuellOppgave(
    oppgaveId: Int
): List<ManuellOppgaveKomplett> =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
                SELECT receivedsykmelding,validationresult,apprec,oppgaveid,ferdigstilt,sendt_apprec,opprinnelig_validationresult
                FROM MANUELLOPPGAVE  
                WHERE oppgaveid=?;
                """,
                )
                .use {
                    it.setInt(1, oppgaveId)
                    it.executeQuery().toList { toManuellOppgave() }
                }
        }
    }

suspend fun DatabaseInterface.hentManuellOppgaveForSykmeldingId(
    sykmeldingId: String
): ManuellOppgaveKomplett? =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """
                SELECT receivedsykmelding,validationresult,apprec,oppgaveid,ferdigstilt,sendt_apprec,opprinnelig_validationresult
                FROM MANUELLOPPGAVE  
                WHERE receivedsykmelding->'sykmelding'->>'id' = ?;
                """,
                )
                .use {
                    it.setString(1, sykmeldingId)
                    it.executeQuery().toList { toManuellOppgave() }.firstOrNull()
                }
        }
    }

suspend fun DatabaseInterface.getUlosteOppgaver(): List<UlosteOppgave> =
    withContext(Dispatchers.IO) {
        connection.use { connection ->
            connection
                .prepareStatement(
                    """select receivedsykmelding->>'mottattDato' as dato, oppgaveId, status FROM MANUELLOPPGAVE
                WHERE ferdigstilt is not true
            """,
                )
                .use { it.executeQuery().toList { toUlostOppgave() } }
        }
    }

fun ResultSet.toUlostOppgave(): UlosteOppgave =
    UlosteOppgave(
        oppgaveId = getInt("oppgaveid"),
        mottattDato = LocalDateTime.parse(getString("dato")),
        status = ManuellOppgaveStatus.valueOf(getString("status")),
    )

fun ResultSet.toManuellOppgave(): ManuellOppgaveKomplett =
    ManuellOppgaveKomplett(
        receivedSykmelding = objectMapper.readValue(getString("receivedsykmelding")),
        validationResult = objectMapper.readValue(getString("validationresult")),
        apprec = objectMapper.readValue(getString("apprec")),
        oppgaveid = getInt("oppgaveid"),
        ferdigstilt = getBoolean("ferdigstilt"),
        sendtApprec = getBoolean("sendt_apprec"),
        opprinneligValidationResult =
            getString("opprinnelig_validationresult")?.let {
                objectMapper.readValue<ValidationResult>(it)
            },
    )
