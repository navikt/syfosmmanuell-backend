package no.nav.syfo.persistering

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.brukernotificasjon.BrukernotifikasjonService
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.erOpprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object HandleReceivedMessageTest : Spek({
    val database = TestDB()
    val oppgaveService = mockk<OppgaveService>()
    val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val kafkaProducers: KafkaProducers = mockk<KafkaProducers>()
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)
    val brukernotifikasjonService = mockk<BrukernotifikasjonService>(relaxed = true)
    val sykmeldingsId = UUID.randomUUID().toString()
    val msgId = "1314"
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(msgId, generateSykmelding(id = sykmeldingsId)),
        validationResult = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("regelnavn", "melding til legen", "melding til bruker", Status.MANUAL_PROCESSING))),
        apprec = objectMapper.readValue(
            Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
                Charsets.UTF_8
            )
        )
    )
    val oppgaveid = 308076319
    val loggingMeta = LoggingMeta("", null, msgId, sykmeldingsId)

    beforeEachTest {
        clearAllMocks()
        coEvery { oppgaveService.opprettOppgave(any(), any()) } returns oppgaveid
        coEvery { kafkaProducers.kafkaApprecProducer.producer } returns mockk()
        coEvery { kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic } returns "sm2013AutomaticHandlingTopic"
        coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    }

    afterEachTest {
        database.connection.dropData()
    }
    afterGroup {
        database.stop()
    }

    describe("Test av mottak av ny melding") {
        it("Happy-case") {
            runBlocking {
                handleReceivedMessage(manuellOppgave, loggingMeta, database, oppgaveService, manuellOppgaveService, brukernotifikasjonService)
            }

            database.hentKomplettManuellOppgave(oppgaveid).size shouldEqual 1
            coVerify { oppgaveService.opprettOppgave(any(), any()) }
            verify(exactly = 1) { brukernotifikasjonService.sendBrukerNotifikasjon(any()) }
        }

        it("Apprec oppdateres") {
            database.erApprecSendt(oppgaveid) shouldEqual false

            runBlocking {
                handleReceivedMessage(manuellOppgave, loggingMeta, database, oppgaveService, manuellOppgaveService, brukernotifikasjonService)
            }

            val hentKomplettManuellOppgave = database.hentKomplettManuellOppgave(oppgaveid)
            hentKomplettManuellOppgave.first().sendtApprec shouldEqual true
            database.erApprecSendt(oppgaveid) shouldEqual true

            coVerify { oppgaveService.opprettOppgave(any(), any()) }
            verify(exactly = 1) { brukernotifikasjonService.sendBrukerNotifikasjon(any()) }
        }

        it("Lagrer ikke melding som allerede finnes") {
            runBlocking {
                handleReceivedMessage(manuellOppgave, loggingMeta, database, oppgaveService, manuellOppgaveService, brukernotifikasjonService)
                handleReceivedMessage(manuellOppgave, loggingMeta, database, oppgaveService, manuellOppgaveService, brukernotifikasjonService)
            }

            database.hentKomplettManuellOppgave(oppgaveid).size shouldEqual 1
            coVerify(exactly = 1) { oppgaveService.opprettOppgave(any(), any()) }
            verify(exactly = 1) { brukernotifikasjonService.sendBrukerNotifikasjon(any()) }
        }
        it("Kaster feil hvis opprettOppgave feilet") {
            coEvery { oppgaveService.opprettOppgave(any(), any()) } throws RuntimeException("Noe gikk galt")
            assertFailsWith<RuntimeException> {
                runBlocking {
                    handleReceivedMessage(manuellOppgave, loggingMeta, database, oppgaveService, manuellOppgaveService, brukernotifikasjonService)
                }
            }
            database.erOpprettManuellOppgave(sykmeldingsId) shouldEqual false
            verify(exactly = 0) { brukernotifikasjonService.sendBrukerNotifikasjon(any()) }
        }
    }
})
