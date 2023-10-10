package no.nav.syfo.persistering

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
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
import no.nav.syfo.testutil.oppgave
import no.nav.syfo.testutil.receivedSykmelding
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions.assertEquals

class MotattSykmeldingServiceTest :
    FunSpec({
        val database = TestDB.database
        val oppgaveService = mockk<OppgaveService>()
        val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
        val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
        val manuellOppgaveService =
            ManuellOppgaveService(
                database,
                syfoTilgangsKontrollClient,
                kafkaProducers,
                oppgaveService
            )
        val mottattSykmeldingService =
            MottattSykmeldingService(
                database = database,
                oppgaveService = oppgaveService,
                manuellOppgaveService = manuellOppgaveService,
            )

        val sykmeldingsId = UUID.randomUUID().toString()
        val msgId = "1314"
        val manuellOppgave =
            ManuellOppgave(
                receivedSykmelding =
                    receivedSykmelding(msgId, generateSykmelding(id = sykmeldingsId)),
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
                        )
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
        val manuellOppgaveString = objectMapper.writeValueAsString(manuellOppgave)
        val oppgaveid = 308076319

        beforeTest {
            clearMocks(syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)
            coEvery { oppgaveService.opprettOppgave(any(), any()) } returns oppgave(oppgaveid)
            coEvery { kafkaProducers.kafkaApprecProducer.producer } returns mockk()
            coEvery { kafkaProducers.kafkaApprecProducer.apprecTopic } returns "apprectopic"
            coEvery { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) } returns
                CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
            coEvery { kafkaProducers.kafkaApprecProducer.producer.send(any()) } returns
                CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        }

        afterTest { database.connection.dropData() }

        context("Test av mottak av ny melding") {
            test("Happy-case") {
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString
                )

                assertEquals(1, database.hentKomplettManuellOppgave(oppgaveid).size)
                coVerify { oppgaveService.opprettOppgave(any(), any()) }
                coVerify { kafkaProducers.kafkaApprecProducer.producer.send(any()) }
                coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
            }

            test("Apprec oppdateres") {
                assertEquals(false, database.erApprecSendt(oppgaveid))

                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString
                )

                val hentKomplettManuellOppgave = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(true, hentKomplettManuellOppgave.first().sendtApprec)
                assertEquals(true, database.erApprecSendt(oppgaveid))

                coVerify { oppgaveService.opprettOppgave(any(), any()) }
            }

            test("Lagrer opprinnelig validation result") {
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString
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
                    manuellOppgaveString
                )
                mottattSykmeldingService.handleMottattSykmelding(
                    sykmeldingsId,
                    manuellOppgaveString
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
                            manuellOppgaveString
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
