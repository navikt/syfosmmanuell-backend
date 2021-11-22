package no.nav.syfo.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.okApprec
import no.nav.syfo.testutil.opprettManuellOppgaveUtenOpprinneligValidationResult
import no.nav.syfo.testutil.receivedSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID
import javax.ws.rs.ForbiddenException
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object ManuellOppgaveServiceTest : Spek({
    val database = TestDB.database
    val syfotilgangskontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveServce = mockk<ManuellOppgaveService>(relaxed = true)
    val sykmeldingsId = UUID.randomUUID().toString()
    val msgId = "1314"
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(msgId, generateSykmelding(id = sykmeldingsId)),
        validationResult = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING))),
        apprec = okApprec()
    )
    val oppgaveid = 308076319

    val manuellOppgaveService = ManuellOppgaveService(database, syfotilgangskontrollClient, kafkaProducers, oppgaveService)

    beforeEachTest {
        database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)
        clearAllMocks()
        coEvery { syfotilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true, null)
        coEvery { manuellOppgaveServce.lagOppdatertApprec(manuellOppgave) } returns manuellOppgave.apprec
    }

    afterEachTest {
        database.connection.dropData()
    }

    describe("Test av ferdigstilling av manuell behandling") {
        it("Happy case OK") {
            runBlocking {
                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveid, "1234",
                    "4321",
                    "token",
                    merknader = null
                )
            }

            coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
            coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
            val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
            oppgaveliste.size shouldEqual 1
            val oppgaveFraDb = oppgaveliste.first()
            oppgaveFraDb.ferdigstilt shouldEqual true
            oppgaveFraDb.opprinneligValidationResult shouldEqual manuellOppgave.validationResult
            oppgaveFraDb.validationResult shouldEqual ValidationResult(Status.OK, emptyList())
            oppgaveFraDb.apprec shouldEqual okApprec()
        }
        it("Happy case OK med merknad") {
            val merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent"))
            runBlocking {
                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveid,
                    "1234",
                    "4321",
                    "token",
                    merknader = merknader
                )
            }

            coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
            coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
            coVerify { oppgaveService.opprettOppfoligingsOppgave(any(), any(), any(), any()) }
            val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
            oppgaveliste.size shouldEqual 1
            val oppgaveFraDb = oppgaveliste.first()
            oppgaveFraDb.ferdigstilt shouldEqual true
            oppgaveFraDb.opprinneligValidationResult shouldEqual manuellOppgave.validationResult
            oppgaveFraDb.validationResult shouldEqual ValidationResult(Status.OK, emptyList())
            oppgaveFraDb.receivedSykmelding.merknader shouldEqual merknader
            oppgaveFraDb.apprec shouldEqual okApprec()
        }
        it("Feiler hvis veileder ikke har tilgang til oppgave") {
            coEvery { syfotilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(false, null)

            assertFailsWith<ForbiddenException> {
                runBlocking {
                    manuellOppgaveService.ferdigstillManuellBehandling(oppgaveid, "1234", "4321", "token", merknader = null)
                }
            }
        }
        it("Setter opprinnelig validation result hvis det mangler ved ferdigstilling") {
            val oppgaveId2 = 998765
            database.connection.opprettManuellOppgaveUtenOpprinneligValidationResult(
                ManuellOppgaveKomplett(
                    receivedSykmelding = receivedSykmelding(msgId, generateSykmelding(id = UUID.randomUUID().toString())),
                    validationResult = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING))),
                    apprec = okApprec(),
                    oppgaveid = oppgaveId2,
                    ferdigstilt = false,
                    sendtApprec = true,
                    opprinneligValidationResult = null
                )
            )
            runBlocking {
                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveId2, "1234",
                    "4321",
                    "token",
                    merknader = null
                )
            }

            coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
            coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
            val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveId2)
            oppgaveliste.size shouldEqual 1
            val oppgaveFraDb = oppgaveliste.first()
            oppgaveFraDb.ferdigstilt shouldEqual true
            oppgaveFraDb.opprinneligValidationResult shouldEqual manuellOppgave.validationResult
            oppgaveFraDb.validationResult shouldEqual ValidationResult(Status.OK, emptyList())
            oppgaveFraDb.apprec shouldEqual okApprec()
            database.erApprecSendt(oppgaveId2) shouldEqual true
        }
    }
})
