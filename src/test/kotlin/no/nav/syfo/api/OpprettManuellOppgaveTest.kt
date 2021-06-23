package no.nav.syfo.api

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OpprettManuellOppgaveTest : Spek({
    val database = TestDB()
    val manuelloppgaveId = "1314"
    val receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding())
    val apprec: Apprec = objectMapper.readValue(
        Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
            Charsets.UTF_8
        )
    )
    val validationResult = ValidationResult(Status.OK, emptyList())
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding,
        validationResult = validationResult,
        apprec = apprec
    )

    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("Test av oppretting av manuelle oppgaver") {
        it("Skal lagre manuellOppgave i databasen og kunne hente den opp som forventet") {
            database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, 123144)

            val oppgaveliste = database.hentKomplettManuellOppgave(123144)

            oppgaveliste.size shouldEqual 1
            oppgaveliste[0].apprec shouldEqual apprec
            oppgaveliste[0].receivedSykmelding shouldEqual receivedSykmelding
            oppgaveliste[0].validationResult shouldEqual validationResult
            oppgaveliste[0].sendtApprec shouldEqual false
        }
        it("Hvis oppgave er lagret for sykmeldingsid skal erOpprettManuellOppgave returnere true") {
            database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, 123144)

            database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id) shouldEqual true
        }
    }
})
