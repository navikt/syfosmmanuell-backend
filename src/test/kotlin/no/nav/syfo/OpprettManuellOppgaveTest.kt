package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.junit.AfterClass
import org.junit.Test

internal class OpprettManuellOppgaveTest {
    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val database = TestDB()

        @AfterClass
        internal fun afterClass() {
            database.connection.dropData()
            database.stop()
        }

        @Test
        internal fun `Skal lagre manuellOppgave i databasen`() {
            val manuelloppgaveId = "1314"

            val manuellOppgave = ManuellOppgave(
                receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
                validationResult = ValidationResult(Status.OK, emptyList()),
                apprec = objectMapper.readValue(Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(Charsets.UTF_8)),
                behandlendeEnhet = "1234"
            )
            database.opprettManuellOppgave(manuellOppgave, "1354", 123144)
        }
}
