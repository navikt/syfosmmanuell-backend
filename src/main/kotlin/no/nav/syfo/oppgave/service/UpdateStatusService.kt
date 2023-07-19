package no.nav.syfo.oppgave.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.seconds

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
            null to ManuellOppgaveStatus.DELETED,
        )
    }

    override suspend fun start() = coroutineScope {
        if (updateJob?.isActive != true) {
            updateJob = launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val oppgaveList = database.getOppgaveWithNullStatus(limit)

                        if (oppgaveList.isEmpty()) break

                        val jobs = oppgaveList.map { (oppgaveId, id) ->
                            launch(Dispatchers.IO) {
                                processOppgave(oppgaveId, id)
                            }
                        }
                        jobs.joinAll()
                    } catch (ex: Exception) {
                        when (ex) {
                            is CancellationException -> {
                                logger.warn("Job was cancelled, message: ${ex.message}")
                                throw ex
                            }
                            else -> {
                                logger.error("Caught unexpected delaying for 10s $ex")
                                delay(10.seconds)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun processOppgave(oppgaveId: Int, id: String) {
        try {
            val oppgave = oppgaveClient.hentOppgave(oppgaveId, id)
            if (oppgave == null) {
                logger.warn("Could not find oppgave for oppgaveId $oppgaveId")
            }

            database.oppdaterOppgaveHendelse(
                oppgaveId = oppgaveId,
                status = statusMap[oppgave?.status] ?: ManuellOppgaveStatus.APEN,
                statusTimestamp = oppgave?.endretTidspunkt?.toLocalDateTime() ?: LocalDateTime.now(),
            )
        } catch (ex: Exception) {
            logger.error("Caught $ex for oppgaveId $oppgaveId")
        }
    }

    override suspend fun stop() {
        updateJob?.cancelAndJoin()
        updateJob = null
    }
}
