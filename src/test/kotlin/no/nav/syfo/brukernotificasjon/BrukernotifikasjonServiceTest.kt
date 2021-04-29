package no.nav.syfo.brukernotificasjon

import io.mockk.mockk
import io.mockk.verify
import no.nav.syfo.brukernotificasjon.db.getBrukerNotifikasjon
import no.nav.syfo.brukernotificasjon.kafka.BeskjedProducer
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.okApprec
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class BrukernotifikasjonServiceTest() : Spek({
    val testDb = TestDB()
    val producer = mockk<BeskjedProducer>(relaxed = true)
    val brukerNotifikasjonService = BrukernotifikasjonService(producer, testDb, "srvsyfosmmanuell-b")

    describe("Test BrukernotifikasjonService") {
        it("Test sending new brukernotifikasjon") {
            val manuellOppgave = ManuellOppgave(
                receivedSykmelding = receivedSykmelding("123"),
                validationResult = ValidationResult(Status.OK, emptyList()),
                apprec = okApprec()
            )
            testDb.getBrukerNotifikasjon(manuellOppgave.receivedSykmelding.sykmelding.id) shouldEqual null
            brukerNotifikasjonService.sendBrukerNotifikasjon(manuellOppgave)

            testDb.getBrukerNotifikasjon(manuellOppgave.receivedSykmelding.sykmelding.id) shouldNotEqual null
            verify(exactly = 1) { producer.sendBeskjed(any(), any()) }
        }
    }
})
