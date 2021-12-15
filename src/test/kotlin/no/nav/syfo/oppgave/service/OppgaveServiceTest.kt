package no.nav.syfo.oppgave.service

import com.fasterxml.jackson.module.kotlin.readValue
import io.mockk.mockk
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
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object OppgaveServiceTest : Spek({
    val oppgaveClient = mockk<OppgaveClient>()
    val kafkaProducer = mockk<KafkaProducers.KafkaProduceTaskProducer>()
    val oppgaveService = OppgaveService(oppgaveClient, kafkaProducer)
    val manuelloppgaveId = "1314"
    val receivedSykmelding = receivedSykmelding(
        manuelloppgaveId,
        generateSykmelding(
            pasientAktoerId = "5555",
            perioder = listOf(
                generatePeriode(
                    fom = LocalDate.of(2020, 8, 1),
                    tom = LocalDate.of(2020, 8, 15)
                )
            )
        )
    )
    val apprec: Apprec = objectMapper.readValue(
        Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(Charsets.UTF_8)
    )
    val validationResult = ValidationResult(Status.OK, emptyList())
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding,
        validationResult = validationResult,
        apprec = apprec
    )

    describe("Test av oppretting av oppgave") {
        it("Oppgave opprettes med riktige parametre") {
            val opprettOppgave = oppgaveService.tilOpprettOppgave(manuellOppgave)

            opprettOppgave.aktoerId shouldBeEqualTo "5555"
            opprettOppgave.opprettetAvEnhetsnr shouldBeEqualTo "9999"
            opprettOppgave.behandlesAvApplikasjon shouldBeEqualTo "SMM"
            opprettOppgave.beskrivelse shouldBeEqualTo "Manuell vurdering av sykmelding for periode: 01.08.2020 - 15.08.2020"
            opprettOppgave.tema shouldBeEqualTo "SYM"
            opprettOppgave.oppgavetype shouldBeEqualTo "BEH_EL_SYM"
            opprettOppgave.behandlingstype shouldBeEqualTo "ae0239"
            opprettOppgave.aktivDato shouldBeEqualTo LocalDate.now()
            opprettOppgave.fristFerdigstillelse shouldNotBeEqualTo null
            opprettOppgave.prioritet shouldBeEqualTo "HOY"
        }
    }

    describe("Test av frist for ferdigstilling") {
        it("Frist blir torsdag hvis oppgaven opprettes på mandag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 7))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 10)
        }
        it("Frist blir fredag hvis oppgaven opprettes på tirsdag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 8))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 11)
        }
        it("Frist blir mandag hvis oppgaven opprettes på onsdag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 9))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 14)
        }
        it("Frist blir tirsdag hvis oppgaven opprettes på torsdag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 10))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 15)
        }
        it("Frist blir onsdag hvis oppgaven opprettes på fredag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 11))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 16)
        }
        it("Frist blir torsdag hvis oppgaven opprettes på lørdag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 12))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 17)
        }
        it("Frist blir torsdag hvis oppgaven opprettes på søndag") {
            val frist = oppgaveService.omTreUkedager(LocalDate.of(2020, 9, 13))

            frist shouldBeEqualTo LocalDate.of(2020, 9, 17)
        }
    }
})
