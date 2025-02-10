package no.nav.syfo.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ManuellOppgaveStatus
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
import org.junit.jupiter.api.Assertions.assertEquals

class ManuellOppgaveServiceTest :
    FunSpec({
        val database = TestDB.database
        val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
        val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
        val oppgaveService = mockk<OppgaveService>(relaxed = true)
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
                        ),
                        OffsetDateTime.now(ZoneOffset.UTC)
                    ),
                apprec = okApprec(),
            )
        val oppgaveid = 308076319

        val manuellOppgaveService =
            ManuellOppgaveService(
                database,
                istilgangskontrollClient,
                kafkaProducers,
                oppgaveService
            )

        beforeTest {
            database.connection.dropData()
            database.opprettManuellOppgave(
                manuellOppgave,
                manuellOppgave.apprec,
                oppgaveid,
                ManuellOppgaveStatus.APEN,
                LocalDateTime.now(),
            )
            clearMocks(kafkaProducers, oppgaveService, istilgangskontrollClient)
            coEvery {
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any())
            } returns Tilgang(true)
        }
        context("test get uloste oppgaver") {
            test("ok") {
                val uloste = manuellOppgaveService.getOppgaver()
                uloste.size shouldBeExactly 1
            }
        }

        context("Test av ferdigstilling av manuell behandling") {
            test("Happy case OK") {
                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveid,
                    "1234",
                    "4321",
                    "token",
                    merknader = null,
                )

                coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
                coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
                val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(1, oppgaveliste.size)
                val oppgaveFraDb = oppgaveliste.first()
                assertEquals(true, oppgaveFraDb.ferdigstilt)
                assertEquals(
                    manuellOppgave.validationResult,
                    oppgaveFraDb.opprinneligValidationResult
                )
                assertEquals(okApprec(), oppgaveFraDb.apprec)
            }
            test("Happy case OK med merknad") {
                val merknader = listOf(Merknad("UGYLDIG_TILBAKEDATERING", "ikke godkjent"))

                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveid,
                    "1234",
                    "4321",
                    "token",
                    merknader = merknader,
                )

                coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
                coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
                coVerify { oppgaveService.opprettOppfolgingsOppgave(any(), any(), any(), any()) }
                val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(1, oppgaveliste.size)
                val oppgaveFraDb = oppgaveliste.first()
                assertEquals(true, oppgaveFraDb.ferdigstilt)
                assertEquals(
                    manuellOppgave.validationResult,
                    oppgaveFraDb.opprinneligValidationResult
                )
                assertEquals(merknader, oppgaveFraDb.receivedSykmelding.merknader)
                assertEquals(okApprec(), oppgaveFraDb.apprec)
            }
            test("Feiler hvis veileder ikke har tilgang til oppgave") {
                coEvery {
                    istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any())
                } returns Tilgang(false)

                assertFailsWith<IkkeTilgangException> {
                    runBlocking {
                        manuellOppgaveService.ferdigstillManuellBehandling(
                            oppgaveid,
                            "1234",
                            "4321",
                            "token",
                            merknader = null
                        )
                    }
                }
            }
            test("Setter opprinnelig validation result hvis det mangler ved ferdigstilling") {
                val oppgaveId2 = 998765
                database.connection.opprettManuellOppgaveUtenOpprinneligValidationResult(
                    ManuellOppgaveKomplett(
                        receivedSykmelding =
                            receivedSykmelding(
                                msgId,
                                generateSykmelding(id = UUID.randomUUID().toString()),
                            ),
                        validationResult =
                            ValidationResult(
                                Status.MANUAL_PROCESSING,
                                listOf(
                                    RuleInfo(
                                        "regelnavn",
                                        "melding til legen",
                                        "melding til bruker",
                                        Status.MANUAL_PROCESSING,
                                    ),
                                ),
                                manuellOppgave.validationResult.timestamp,
                            ),
                        apprec = okApprec(),
                        oppgaveid = oppgaveId2,
                        ferdigstilt = false,
                        sendtApprec = true,
                        opprinneligValidationResult = null,
                    ),
                )

                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveId2,
                    "1234",
                    "4321",
                    "token",
                    merknader = null,
                )

                coVerify { kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(any()) }
                coVerify { oppgaveService.ferdigstillOppgave(any(), any(), any(), any()) }
                val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveId2)
                assertEquals(1, oppgaveliste.size)
                val oppgaveFraDb = oppgaveliste.first()
                assertEquals(true, oppgaveFraDb.ferdigstilt)
                assertEquals(
                    manuellOppgave.validationResult,
                    oppgaveFraDb.opprinneligValidationResult
                )
                assertEquals(oppgaveFraDb.apprec, okApprec())
                assertEquals(true, database.erApprecSendt(oppgaveId2))
            }
            test("Sletter manuell oppgave og ferdigstiller åpen oppgave") {
                manuellOppgaveService.slettOppgave(sykmeldingsId)

                val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(0, oppgaveliste.size)
                coVerify {
                    oppgaveService.ferdigstillOppgave(
                        any(),
                        any(),
                        matchNullable { it == null },
                        matchNullable { it == null }
                    )
                }
            }
            test("Sletter manuell oppgave og ferdigstiller ikke ferdigstilt oppgave") {
                manuellOppgaveService.ferdigstillManuellBehandling(
                    oppgaveid,
                    "1234",
                    "4321",
                    "token",
                    merknader = emptyList(),
                )

                manuellOppgaveService.slettOppgave(sykmeldingsId)

                val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(0, oppgaveliste.size)
                coVerify { oppgaveService.ferdigstillOppgave(any(), any(), eq("1234"), eq("4321")) }
                coVerify(exactly = 0) {
                    oppgaveService.ferdigstillOppgave(
                        any(),
                        any(),
                        matchNullable { it == null },
                        matchNullable { it == null }
                    )
                }
            }
            test("Sletter ikke andre manuelle oppgaver") {
                manuellOppgaveService.slettOppgave(UUID.randomUUID().toString())

                val oppgaveliste = database.hentKomplettManuellOppgave(oppgaveid)
                assertEquals(1, oppgaveliste.size)
                coVerify(exactly = 0) {
                    oppgaveService.ferdigstillOppgave(any(), any(), any(), any())
                }
            }
        }
    })
