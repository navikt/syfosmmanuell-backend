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
import org.junit.Test

internal class OpprettManuellOppgaveTest {

    private val database = TestDB()

    @Test
    internal fun `Skal lagre manuellOppgave i databasen`() {
        val manuelloppgaveId = "1314"

        val manuellOppgave = ManuellOppgave(
            receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
            validationResult = ValidationResult(Status.OK, emptyList()),
            apprec = objectMapper.readValue(
                Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
                    Charsets.UTF_8
                )
            )
        )
        database.opprettManuellOppgave(manuellOppgave, 123144)
        database.hentKomplettManuellOppgave(123144).size shouldEqual 1

        database.connection.dropData()
    }

    @Test
    internal fun `Skal ikkje lagre duplikat manuellOppgave i databasen, basert på sykmeldings id`() {
        val manuelloppgaveId = "1314"

        val manuellOppgave = ManuellOppgave(
            receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
            validationResult = ValidationResult(Status.OK, emptyList()),
            apprec = objectMapper.readValue(
                Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
                    Charsets.UTF_8
                )
            )
        )
        database.opprettManuellOppgave(manuellOppgave, 123144)
        database.erOpprettManuellOppgave(manuellOppgave.receivedSykmelding.sykmelding.id) shouldEqual true

        database.connection.dropData()
    }
}
