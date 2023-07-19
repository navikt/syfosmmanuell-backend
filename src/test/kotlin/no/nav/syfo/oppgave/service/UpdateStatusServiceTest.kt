package no.nav.syfo.oppgave.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.oppgave.OpprettOppgaveResponse
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.persistering.db.getOppgaveWithNullStatus
import no.nav.syfo.persistering.db.oppdaterOppgaveHendelse
import java.time.LocalDateTime

class UpdateStatusServiceTest : FunSpec({
    val database: DatabaseInterface = mockk(relaxed = true)
    val oppgaveClient: OppgaveClient = mockk()
    lateinit var service: UpdateStatusService
    mockkStatic("no.nav.syfo.persistering.db.PersisterManuellOppgaveQueriesKt")

    beforeTest {
        service = UpdateStatusService(database, oppgaveClient)
    }

    test("`start` should fetch oppgave and update their status") {
        runTest {
            val oppgaveId = 1
            val id = "id"
            val status = "FERDIGSTILT"
            val oppgaveList = listOf(oppgaveId to id)
            val localTime = LocalDateTime.now()
            val oppgave = OpprettOppgaveResponse(oppgaveId, 1, status, endretTidspunkt = localTime)

            coEvery { database.getOppgaveWithNullStatus(10) } returns
                oppgaveList andThen emptyList()
            coEvery { oppgaveClient.hentOppgave(oppgaveId, id) } returns oppgave

            service.start()

            coVerify {
                database.oppdaterOppgaveHendelse(
                    oppgaveId = oppgaveId,
                    status = ManuellOppgaveStatus.FERDIGSTILT,
                    statusTimestamp = oppgave.endretTidspunkt!!,
                )
            }
        }
    }
})
