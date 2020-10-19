package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import javax.ws.rs.ForbiddenException
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.client.Veileder
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object ManuellOppgaveServiceTest : Spek({
    val database = TestDB()
    val syfotilgangskontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val authorizationService = AuthorizationService(syfotilgangskontrollClient)
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val sykmeldingsId = UUID.randomUUID().toString()
    val msgId = "1314"
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(msgId, generateSykmelding(id = sykmeldingsId)),
        validationResult = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING))),
        apprec = okApprec()
    )
    val oppgaveid = 308076319

    val manuellOppgaveService = ManuellOppgaveService(database, authorizationService, kafkaProducers, oppgaveService)

    beforeEachTest {
        database.opprettManuellOppgave(manuellOppgave, oppgaveid)
        clearAllMocks()
        coEvery { syfotilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, null)
        coEvery { syfotilgangskontrollClient.hentVeilderIdentViaAzure(any()) } returns Veileder(veilederIdent = "4321")
    }

    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("Test av ferdigstilling av manuell behandling") {
        it("Happy case OK") {
            runBlocking {
                manuellOppgaveService.ferdigstillManuellBehandling(oppgaveid, "1234", ValidationResult(Status.OK, emptyList()), "token")
            }

            coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
            coVerify(exactly = 0) { kafkaProducers.kafkaValidationResultProducer.producer.send(any()) }
            coVerify { kafkaProducers.kafkaSyfoserviceProducer.producer.send(any()) }
            coVerify { kafkaProducers.kafkaApprecProducer.producer.send(any()) }
            coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
            val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
            oppgaveliste.size shouldEqual 1
            val oppgaveFraDb = oppgaveliste.first()
            oppgaveFraDb.ferdigstilt shouldEqual true
            oppgaveFraDb.validationResult shouldEqual ValidationResult(Status.OK, emptyList())
            oppgaveFraDb.apprec shouldEqual okApprec()
        }
        it("Happy case AVVIST") {
            runBlocking {
                manuellOppgaveService.ferdigstillManuellBehandling(oppgaveid, "1234", ValidationResult(Status.INVALID, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.INVALID))), "token")
            }

            coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
            coVerify { kafkaProducers.kafkaValidationResultProducer.producer.send(any()) }
            coVerify(exactly = 0) { kafkaProducers.kafkaSyfoserviceProducer.producer.send(any()) }
            coVerify { kafkaProducers.kafkaApprecProducer.producer.send(any()) }
            coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
            val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
            oppgaveliste.size shouldEqual 1
            val oppgaveFraDb = oppgaveliste.first()
            println(objectMapper.writeValueAsString(oppgaveFraDb.apprec))
            oppgaveFraDb.ferdigstilt shouldEqual true
            oppgaveFraDb.validationResult shouldEqual ValidationResult(Status.INVALID, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.INVALID)))
            oppgaveFraDb.apprec shouldEqual avvistApprec()
        }
        it("Feiler hvis validation result er MANUAL_PROCESSING") {
            assertFailsWith<IllegalArgumentException> {
                runBlocking {
                    manuellOppgaveService.ferdigstillManuellBehandling(oppgaveid, "1234", ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING))), "token")
                }
            }
        }
        it("Feiler hvis veileder ikke har tilgang til oppgave") {
            coEvery { syfotilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(false, null)

            assertFailsWith<ForbiddenException> {
                runBlocking {
                    manuellOppgaveService.ferdigstillManuellBehandling(oppgaveid, "1234", ValidationResult(Status.OK, emptyList()), "token")
                }
            }
        }
    }
})

private fun okApprec(): Apprec {
    return objectMapper.readValue(
        Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
            Charsets.UTF_8
        )
    )
}

private fun avvistApprec(): Apprec {
    return objectMapper.readValue(
        Apprec::class.java.getResourceAsStream("/apprecAvvist.json").readBytes().toString(
            Charsets.UTF_8
        )
    )
}
