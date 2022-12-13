package no.nav.syfo.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
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
import org.junit.jupiter.api.Assertions.assertEquals

class OpprettManuellOppgaveTest : FunSpec({
    val database = TestDB.database
    val manuelloppgaveId = "1314"
    val receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding())
    val apprec: Apprec = objectMapper.readValue(
        Apprec::class.java.getResourceAsStream("/apprecOK.json")!!.readBytes().toString(
            Charsets.UTF_8
        )
    )
    val validationResult = ValidationResult(Status.OK, emptyList())
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding,
        validationResult = validationResult,
        apprec = apprec
    )

    afterTest {
        database.connection.dropData()
    }

    context("Test av oppretting av manuelle oppgaver") {
        test("Skal lagre manuellOppgave i databasen og kunne hente den opp som forventet") {
            database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, 123144)

            val oppgaveliste = database.hentKomplettManuellOppgave(123144)

            assertEquals(1, oppgaveliste.size)
            assertEquals(apprec, oppgaveliste[0].apprec)
            assertEquals(receivedSykmelding, oppgaveliste[0].receivedSykmelding)
            assertEquals(validationResult, oppgaveliste[0].validationResult)
            assertEquals(false, oppgaveliste[0].sendtApprec)
        }
        test("Hvis oppgave er lagret for sykmeldingsid skal erOpprettManuellOppgave returnere true") {
            database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, 123144)

            assertEquals(true, database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id))
        }
    }
})
