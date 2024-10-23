package no.nav.syfo.oppgave.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.getEnvVar
import no.nav.syfo.logger
import no.nav.syfo.metrics.GJENOPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.oppgave.*
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.oppgave.model.PrioritetType
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.ProducerRecord

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val kafkaProduceTaskProducer: KafkaProducers.KafkaProduceTaskProducer,
) {

    suspend fun opprettOppgave(
        manuellOppgave: ManuellOppgave,
        loggingMeta: LoggingMeta
    ): OpprettOppgaveResponse {
        val opprettOppgave = tilOpprettOppgave(manuellOppgave)
        val oppgaveResponse =
            oppgaveClient.opprettOppgave(opprettOppgave, manuellOppgave.receivedSykmelding.msgId)
        OPPRETT_OPPGAVE_COUNTER.inc()
        logger.info(
            "Opprettet manuell sykmeldingsoppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
            StructuredArguments.fields(loggingMeta),
        )
        return oppgaveResponse
    }

    suspend fun gjenopprettOppgave(
        manuellOppgave: ManuellOppgaveKomplett,
        loggingMeta: LoggingMeta
    ): OpprettOppgaveResponse {
        val oppgave =
            oppgaveClient.hentOppgave(
                manuellOppgave.oppgaveid,
                manuellOppgave.receivedSykmelding.msgId
            )

        logger.info("fant oppgave vi skal gjenopprette: $oppgave")
        requireNotNull(oppgave) {
            throw RuntimeException("Could not find oppgave for ${manuellOppgave.oppgaveid}")
        }

        val gjenopprettOppgave = tilGjenopprettOppgave(oppgave, manuellOppgave)
        logger.info("Forsøker å gjenopprette oppgave $gjenopprettOppgave, $loggingMeta")

        val oppgaveResponse =
            oppgaveClient.opprettOppgave(
                gjenopprettOppgave,
                manuellOppgave.receivedSykmelding.msgId
            )
        GJENOPPRETT_OPPGAVE_COUNTER.inc()
        logger.info(
            "Gjennopprettet manuell sykmeldingsoppgave med oppgaveId ${oppgaveResponse.id}, lg hele responsen $oppgaveResponse"
        )
        return oppgaveResponse
    }

    fun tilGjenopprettOppgave(
        oppgave: OpprettOppgaveResponse,
        manuellOppgave: ManuellOppgaveKomplett
    ): OpprettOppgave =
        OpprettOppgave(
            aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
            opprettetAvEnhetsnr = "9999",
            behandlesAvApplikasjon = "SMM",
            beskrivelse =
                oppgave.beskrivelse.plus(
                    "\n" +
                        "SyfosmManuell: oppgaven er ferdigstilt i gosys, " +
                        "men må ferdigstilles i syfosmmanuell for at den skal digitaliseres"
                ),
            tema = "SYM",
            oppgavetype = "BEH_EL_SYM",
            behandlingstype = "ae0239",
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = oppgave.fristFerdigstillelse ?: omTreUkedager(LocalDate.now()),
            prioritet = "HOY",
        )

    suspend fun endreOppgave(
        manuellOppgave: ManuellOppgaveKomplett,
        loggingMeta: LoggingMeta,
    ) {
        val oppgave =
            oppgaveClient.hentOppgave(
                manuellOppgave.oppgaveid,
                manuellOppgave.receivedSykmelding.msgId
            )
        requireNotNull(oppgave) {
            throw RuntimeException("Could not find oppgave for ${manuellOppgave.oppgaveid}")
        }
        val endretBeskrivelse =
            "\nSyfosmManuell: Trenger flere opplysninger før denne oppgaven kan ferdigstilles. Du kan ferdigstille oppgaven i appen når vi har mottatt etterlyst dokumentasjon og er klare til å fatte en beslutning i saken. \n SyfosmManuell: Fjernet eksisterende saksbehandler fra saken."
        val oppgaveEnhet = getEnvVar("OPPGAVE_ENHET")
        val endreOppgave =
            EndreOppgave(
                versjon = oppgave.versjon,
                id = manuellOppgave.oppgaveid,
                beskrivelse = oppgave.beskrivelse?.plus(endretBeskrivelse) ?: endretBeskrivelse,
                fristFerdigstillelse = omToUker(LocalDate.now()),
                mappeId =
                    if (oppgave.tildeltEnhetsnr == oppgaveEnhet) {
                        getEnvVar("OPPGAVE_MAPPE_ID").toInt()
                    } else {
                        // Det skaper trøbbel i Oppgave-apiet hvis enheten som blir satt ikke
                        // har den aktuelle mappen
                        null
                    },
                mappeNavn = getEnvVar("OPPGAVE_MAPPENAVN"),
                tildeltEnhetsnr = oppgaveEnhet,
            )
        logger.info(
            "Forsøker å endre oppgavebeskrivelse, mappe og enhet på oppgave som trenger flere opplysninger {}, {}. \n der mappeId var {} og er satt til id: {} med navn: {}",
            StructuredArguments.fields(endreOppgave),
            StructuredArguments.fields(loggingMeta),
            oppgave.mappeId,
            endreOppgave.mappeId,
            endreOppgave.mappeNavn
        )
        val oppgaveResponse =
            oppgaveClient.endreOppgave(endreOppgave, manuellOppgave.receivedSykmelding.msgId)
        logger.info(
            "Endret oppgave på oppgave som trenger flere opplysninger med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
            StructuredArguments.fields(loggingMeta),
        )
    }

    fun opprettOppfolgingsOppgave(
        manuellOppgave: ManuellOppgaveKomplett,
        enhet: String,
        veileder: String,
        loggingMeta: LoggingMeta
    ) {
        val opprettOppgaveKafkaMessage = tilOppfolgingsoppgave(manuellOppgave, enhet, veileder)
        val producerRecord =
            ProducerRecord(
                kafkaProduceTaskProducer.topic,
                manuellOppgave.receivedSykmelding.sykmelding.id,
                opprettOppgaveKafkaMessage,
            )

        try {
            kafkaProduceTaskProducer.producer.send(producerRecord).get()
        } catch (e: Exception) {
            logger.error("Sending til {} feilet", kafkaProduceTaskProducer.topic)
            throw e
        }

        logger.info(
            "Opprettelse av oppfølgingsoppgave forespurt for sykmelding med merknad {}",
            StructuredArguments.fields(loggingMeta),
        )
    }

    suspend fun ferdigstillOppgave(
        manuellOppgave: ManuellOppgaveKomplett,
        loggingMeta: LoggingMeta,
        enhet: String?,
        veileder: String?
    ) {
        val oppgave =
            oppgaveClient.hentOppgave(
                manuellOppgave.oppgaveid,
                manuellOppgave.receivedSykmelding.msgId
            )
        val tildeltEnhet = enhet ?: oppgave?.tildeltEnhetsnr
        if (enhet == null) {
            logger.warn(
                "Enhet er null, bruker tildelt enhet fra oppgaven ${oppgave?.tildeltEnhetsnr} for id ${manuellOppgave.oppgaveid}"
            )
        }
        requireNotNull(oppgave) {
            throw RuntimeException("Could not find oppgave for ${manuellOppgave.oppgaveid}")
        }
        val oppgaveVersjon = oppgave.versjon

        if (oppgave.status != OppgaveStatus.FERDIGSTILT.name) {
            val ferdigstillOppgave =
                FerdigstillOppgave(
                    versjon = oppgaveVersjon,
                    id = manuellOppgave.oppgaveid,
                    status = OppgaveStatus.FERDIGSTILT,
                    tildeltEnhetsnr = tildeltEnhet,
                    tilordnetRessurs = veileder,
                    mappeId =
                        if (oppgave.tildeltEnhetsnr == enhet) {
                            oppgave.mappeId
                        } else {
                            // Det skaper trøbbel i Oppgave-apiet hvis enheten som blir satt ikke
                            // har den aktuelle mappen
                            null
                        },
                )

            logger.info(
                "Forsøker å ferdigstille oppgave {}, {}",
                StructuredArguments.fields(ferdigstillOppgave),
                StructuredArguments.fields(loggingMeta)
            )

            val oppgaveResponse =
                oppgaveClient.ferdigstillOppgave(
                    ferdigstillOppgave,
                    manuellOppgave.receivedSykmelding.msgId
                )
            logger.info(
                "Ferdigstilt oppgave med {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
                StructuredArguments.fields(loggingMeta),
            )
        } else {
            logger.info(
                "Oppgaven er allerede ferdigstillt oppgaveId: ${oppgave.id} {}",
                StructuredArguments.fields(loggingMeta)
            )
        }
    }

    fun tilOpprettOppgave(manuellOppgave: ManuellOppgave): OpprettOppgave =
        OpprettOppgave(
            aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
            opprettetAvEnhetsnr = "9999",
            behandlesAvApplikasjon = "SMM",
            beskrivelse =
                "Manuell vurdering av sykmelding for periode: ${getFomTomTekst(manuellOppgave.receivedSykmelding)}",
            tema = "SYM",
            oppgavetype = "BEH_EL_SYM",
            behandlingstype = "ae0239",
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = omTreUkedager(LocalDate.now()),
            prioritet = "HOY",
        )

    fun tilOppfolgingsoppgave(
        manuellOppgave: ManuellOppgaveKomplett,
        enhet: String,
        veileder: String
    ): OpprettOppgaveKafkaMessage =
        OpprettOppgaveKafkaMessage(
            messageId = manuellOppgave.receivedSykmelding.msgId,
            aktoerId = manuellOppgave.receivedSykmelding.sykmelding.pasientAktoerId,
            tildeltEnhetsnr = enhet,
            opprettetAvEnhetsnr = "9999",
            behandlesAvApplikasjon = "FS22", // Gosys
            orgnr = manuellOppgave.receivedSykmelding.legekontorOrgNr ?: "",
            beskrivelse =
                "Oppfølgingsoppgave for sykmelding registrert med merknad " +
                    manuellOppgave.receivedSykmelding.merknader?.joinToString { it.type },
            temagruppe = "ANY",
            tema = "SYM",
            behandlingstema = "ANY",
            oppgavetype = "BEH_EL_SYM",
            behandlingstype = "ANY",
            mappeId = 1,
            aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now()),
            fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now()),
            prioritet = PrioritetType.HOY,
            metadata = mapOf("tilordnetRessurs" to veileder),
        )

    fun omTreUkedager(idag: LocalDate): LocalDate =
        when (idag.dayOfWeek) {
            DayOfWeek.SUNDAY -> idag.plusDays(4)
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY -> idag.plusDays(3)
            else -> idag.plusDays(5)
        }

    fun omToUker(idag: LocalDate): LocalDate = idag.plusWeeks(2)

    private fun getFomTomTekst(receivedSykmelding: ReceivedSykmelding) =
        "${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
            " ${formaterDato(receivedSykmelding.sykmelding.perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

    private fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> = sortedBy { it.fom }

    private fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> = sortedBy { it.tom }

    private fun formaterDato(dato: LocalDate): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        return dato.format(formatter)
    }
}
