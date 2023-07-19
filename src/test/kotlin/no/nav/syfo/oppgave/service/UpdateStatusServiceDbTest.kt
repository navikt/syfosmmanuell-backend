package no.nav.syfo.oppgave.service

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.okApprec
import no.nav.syfo.testutil.receivedSykmelding
import java.util.UUID

class UpdateStatusServiceDbTest : FunSpec({
    val database: DatabaseInterface = TestDB.database
    val oppgaveClient: OppgaveClient = mockk()
    val service: UpdateStatusService = UpdateStatusService(database, oppgaveClient)
    mockkStatic("no.nav.syfo.persistering.db.PersisterManuellOppgaveQueriesKt")
    coEvery { oppgaveClient.hentOppgave(any(), any()) } returns null
    beforeTest {
        database.opprettManuellOppgave(
            manuellOppgave = ManuellOppgave(
                receivedSykmelding = receivedSykmelding(UUID.randomUUID().toString()),
                validationResult = ValidationResult(Status.OK, emptyList()),
                apprec = okApprec(),
            ),
            okApprec(),
            0,
        )
    }

    test("Update status") {
        service.start()
    }
})
