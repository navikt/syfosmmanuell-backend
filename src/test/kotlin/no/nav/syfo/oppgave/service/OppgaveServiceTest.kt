package no.nav.syfo.oppgave.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import java.time.LocalDate
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.testutil.generatePeriode
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.junit.jupiter.api.Assertions.assertEquals

class OppgaveServiceTest :
    FunSpec({
        val oppgaveClient = mockk<OppgaveClient>()
        val kafkaProducer = mockk<KafkaProducers.KafkaProduceTaskProducer>()
        val oppgaveService = OppgaveService(oppgaveClient, kafkaProducer)
        val manuelloppgaveId = "1314"
        val receivedSykmelding =
            receivedSykmelding(
                manuelloppgaveId,
                generateSykmelding(
                    pasientAktoerId = "5555",
                    perioder =
                        listOf(
                            generatePeriode(
                                fom = LocalDate.of(2020, 8, 1),
                                tom = LocalDate.of(2020, 8, 15),
                            ),
                        ),
                ),
            )
        val apprec: Apprec =
            objectMapper.readValue(
                Apprec::class
                    .java
                    .getResourceAsStream("/apprecOK.json")!!
                    .readBytes()
                    .toString(Charsets.UTF_8),
            )
        val validationResult = ValidationResult(Status.OK, emptyList())
        val manuellOppgave =
            ManuellOppgave(
                receivedSykmelding = receivedSykmelding,
                validationResult = validationResult,
                apprec = apprec,
            )

        context("Test av oppretting av oppgave") {
            test("Oppgave opprettes med riktige parametre") {
                val opprettOppgave = oppgaveService.tilOpprettOppgave(manuellOppgave)

                assertEquals("5555", opprettOppgave.aktoerId)
                assertEquals("9999", opprettOppgave.opprettetAvEnhetsnr)
                assertEquals("SMM", opprettOppgave.behandlesAvApplikasjon)
                assertEquals(
                    "Manuell vurdering av sykmelding for periode: 01.08.2020 - 15.08.2020",
                    opprettOppgave.beskrivelse
                )
                assertEquals("SYM", opprettOppgave.tema)
                assertEquals("BEH_EL_SYM", opprettOppgave.oppgavetype)
                assertEquals("ae0239", opprettOppgave.behandlingstype)
                assertEquals(LocalDate.now(), opprettOppgave.aktivDato)
                assertEquals(
                    oppgaveService.omTreUkedager(LocalDate.now()),
                    opprettOppgave.fristFerdigstillelse
                )
                assertEquals("HOY", opprettOppgave.prioritet)
            }
        }

        context("Test av frist for ferdigstilling") {
            test("Frist blir torsdag hvis oppgaven opprettes på mandag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 7))

                assertEquals(LocalDate.of(2020, 9, 10), frist)
            }
            test("Frist blir fredag hvis oppgaven opprettes på tirsdag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 8))

                assertEquals(LocalDate.of(2020, 9, 11), frist)
            }
            test("Frist blir mandag hvis oppgaven opprettes på onsdag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 9))

                assertEquals(LocalDate.of(2020, 9, 14), frist)
            }
            test("Frist blir tirsdag hvis oppgaven opprettes på torsdag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 10))

                assertEquals(LocalDate.of(2020, 9, 15), frist)
            }
            test("Frist blir onsdag hvis oppgaven opprettes på fredag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 11))

                assertEquals(LocalDate.of(2020, 9, 16), frist)
            }
            test("Frist blir torsdag hvis oppgaven opprettes på lørdag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 12))

                assertEquals(LocalDate.of(2020, 9, 17), frist)
            }
            test("Frist blir torsdag hvis oppgaven opprettes på søndag") {
                val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 13))

                assertEquals(LocalDate.of(2020, 9, 17), frist)
            }
        }
    })
