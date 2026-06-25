package no.nav.syfo.persistering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaveForSykmeldingId
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
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
import no.nav.syfo.testutil.oppgave
import no.nav.syfo.testutil.receivedSykmelding
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.assertEquals

class MottattSykmeldingServiceTest :
    FunSpec({
        val database = TestDB.database
        val oppgaveService = mockk<OppgaveService>()
        val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
        val manuellOppgaveService =
            ManuellOppgaveService(database, kafkaProducers, oppgaveService, "app", "namespace")
        val behandlingsdagId = UUID.randomUUID().toString()
        val mottattSykmeldingService =
            MottattSykmeldingService(
                database = database,
                oppgaveService = oppgaveService,
                manuellOppgaveService = manuellOppgaveService,
                behandlingsdagerIds = listOf(behandlingsdagId),
            )

        val receivedSykmeldingProducer =
            mockk<KafkaProducer<String, ReceivedSykmeldingWithValidation>>()
        val sykmeldingsId = UUID.randomUUID().toString()
        val msgId = "1314"
        val manuellOppgave = oppgave(msgId, sykmeldingsId)
        val manuellOppgaveString = objectMapper.writeValueAsString(manuellOppgave)
        val oppgaveid = 308076319

        beforeTest {
            clearMocks(kafkaProducers, oppgaveService, receivedSykmeldingProducer)
            coEvery { oppgaveService.opprettOppgave(any(), any()) } returns oppgave(oppgaveid)
            coEvery { kafkaProducers.kafkaApprecProducer.apprecTopic } returns "apprectopic"
            coEvery { kafkaProducers.kafkaApprecProducer.producer } returns mockk()
            coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer } returns
                receivedSykmeldingProducer
            coEvery { receivedSykmeldingProducer.send(any()) } returns
                CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
            coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns
                CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        }

        afterTest { database.connection.dropData() }

        context("Test av mottak av ny melding") {
            test("tombstone behandlingsdag should cleanup") {
                val oppgaveid = 123
                coEvery { oppgaveService.feilregistrerOppgave(any(), any()) } returns Unit
                coEvery { oppgaveService.opprettOppgave(any(), any()) } returns oppgave(oppgaveid)
                mottattSykmeldingService.handleMottattSykmelding(
                    behandlingsdagId,
                    objectMapper.writeValueAsString(
                        oppgave(msgId = behandlingsdagId, sykmeldingsId = behandlingsdagId)
                    ),
                    emptyMap()
                )

                assertEquals(1, database.hentKomplettManuellOppgave(oppgaveid).size)
                assertNotNull(database.hentManuellOppgaveForSykmeldingId(behandlingsdagId))

                mottattSykmeldingService.handleMottattSykmelding(behandlingsdagId, null, emptyMap())

                assertEquals(0, database.hentKomplettManuellOppgave(oppgaveid).size)
                assertNull(database.hentManuellOppgaveForSykmeldingId(behandlingsdagId))

                coVerifyOrder {
                    oppgaveService.opprettOppgave(any(), any())
                    receivedSykmeldingProducer.send(
                        match {
                            it.value().merknader ==
                                listOf(
                                    Merknad(
                                        "UNDER_BEHANDLING",
                                        "Sykmeldingen er til manuell behandling"
                                    )
                                ) && it.value().validationResult.status == Status.OK
                        }
                    )
                    oppgaveService.feilregistrerOppgave(any(), any())
                    receivedSykmeldingProducer.send(
                        match {
                            it.value().merknader == null &&
                                it.value().validationResult.status == Status.OK
                        }
                    )
                }
            }

            test("Happy-case") {
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString,
                    emptyMap()
                )

                assertEquals(1, database.hentKomplettManuellOppgave(oppgaveid).size)
                coVerify { oppgaveService.opprettOppgave(any(), any()) }
                coVerify { kafkaProducers.kafkaApprecProducer.producer.send(any()) }
                coVerify { receivedSykmeldingProducer.send(any()) }
            }
            test("Save manuellOppgave from syk-inn (apprec is null)") {
                val manuellOppgave = manuellOppgave.copy(apprec = null)
                val manuellOppgaveString = objectMapper.writeValueAsString(manuellOppgave)
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString,
                    emptyMap()
                )
                val oppgave = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(null, oppgave.first().apprec)
            }
            test("Apprec oppdateres") {
                assertEquals(false, database.erApprecSendt(oppgaveid))

                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString,
                    emptyMap()
                )

                val hentKomplettManuellOppgave = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(true, hentKomplettManuellOppgave.first().sendtApprec)
                assertEquals(true, database.erApprecSendt(oppgaveid))

                coVerify { oppgaveService.opprettOppgave(any(), any()) }
            }

            test("Lagrer opprinnelig validation result") {
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString,
                    emptyMap(),
                )

                val komplettManuellOppgave = database.hentKomplettManuellOppgave(oppgaveid).first()
                assertEquals(
                    komplettManuellOppgave.validationResult,
                    komplettManuellOppgave.opprinneligValidationResult
                )
            }

            test("Lagrer ikke melding som allerede finnes") {
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString,
                    emptyMap(),
                )
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString,
                    emptyMap(),
                )

                assertEquals(1, database.hentKomplettManuellOppgave(oppgaveid).size)
                coVerify(exactly = 1) { oppgaveService.opprettOppgave(any(), any()) }
            }
            test("Kaster feil hvis opprettOppgave feilet") {
                coEvery { oppgaveService.opprettOppgave(any(), any()) } throws
                    RuntimeException("Noe gikk galt")
                assertFailsWith<RuntimeException> {
                    runBlocking {
                        mottattSykmeldingService.handleMottattSykmelding(
                            sykmeldingsId,
                            manuellOppgaveString,
                            emptyMap()
                        )
                    }
                }
                assertEquals(false, database.erOpprettManuellOppgave(sykmeldingsId))
                coVerify(exactly = 0) {
                    kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any())
                }
            }
        }
    })

private fun oppgave(msgId: String, sykmeldingsId: String): ManuellOppgave =
    ManuellOppgave(
        receivedSykmelding = receivedSykmelding(msgId, generateSykmelding(id = sykmeldingsId)),
        validationResult =
            ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(
                    RuleInfo(
                        "regelnavn",
                        "melding til legen",
                        "melding til bruker",
                        Status.MANUAL_PROCESSING
                    )
                ),
                OffsetDateTime.now(ZoneOffset.UTC),
            ),
        apprec =
            objectMapper.readValue(
                Apprec::class
                    .java
                    .getResourceAsStream("/apprecOK.json")!!
                    .readBytes()
                    .toString(
                        Charsets.UTF_8,
                    ),
            ),
    )
