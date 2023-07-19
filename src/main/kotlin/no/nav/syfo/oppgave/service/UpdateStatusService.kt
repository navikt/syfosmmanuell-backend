package no.nav.syfo.oppgave.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.persistering.db.getOppgaveWithNullStatus
import no.nav.syfo.persistering.db.oppdaterOppgaveHendelse
import no.nav.syfo.service.UpdateService
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class UpdateStatusService(
    private val database: DatabaseInterface,
    private val oppgaveClient: OppgaveClient,
) : UpdateService {

    private var updateJob: Job? = null
    private val limit = 10
    private val logger = LoggerFactory.getLogger(UpdateStatusService::class.java)

    companion object {
        private val statusMap = mapOf(
            "FERDIGSTILT" to ManuellOppgaveStatus.FERDIGSTILT,
            "FEILREGISTRERT" to ManuellOppgaveStatus.FEILREGISTRERT,
        )
    }

    override suspend fun start() = coroutineScope {
        if (updateJob?.isActive != true) {
            updateJob = launch(Dispatchers.IO) {
                while (isActive) {
                    val oppgaveList = database.getOppgaveWithNullStatus(limit)

                    // Breaks the loop if there are no more records
                    if (oppgaveList.isEmpty()) break

                    val jobs = oppgaveList.map { (oppgaveId, id) ->
                        launch(Dispatchers.IO) {
                            try {
                                val oppgave = oppgaveClient.hentOppgave(oppgaveId, id)
                                database.oppdaterOppgaveHendelse(
                                    oppgaveId = oppgaveId,
                                    status = statusMap[oppgave.status] ?: ManuellOppgaveStatus.APEN,
                                    statusTimestamp = oppgave.endretTidspunkt?.toLocalDateTime() ?: LocalDateTime.now(),
                                )
                            } catch (ex: Exception) {
                                logger.error("Caught $ex for oppgaveId $oppgaveId")
                            }
                        }
                    }
                    jobs.joinAll()
                }
            }
        }
    }

    override fun stop() {
        updateJob?.cancel()
        updateJob = null
    }
}
