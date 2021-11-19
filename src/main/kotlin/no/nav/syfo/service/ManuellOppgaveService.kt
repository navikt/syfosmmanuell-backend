package no.nav.syfo.service

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.ws.rs.ForbiddenException
import javax.xml.bind.Unmarshaller
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.finnesOppgave
import no.nav.syfo.aksessering.db.finnesSykmelding
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.FERDIGSTILT_OPPGAVE_COUNTER
import no.nav.syfo.metrics.MERKNAD_COUNTER
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.oppdaterApprecStatus
import no.nav.syfo.persistering.db.oppdaterManuellOppgave
import no.nav.syfo.persistering.db.oppdaterManuellOppgaveUtenOpprinneligValidationResult
import no.nav.syfo.persistering.error.OppgaveNotFoundException
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.XMLDateAdapter
import no.nav.syfo.util.XMLDateTimeAdapter
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatJaxBContext
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
class ManuellOppgaveService(
    private val database: DatabaseInterface,
    private val syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient,
    private val kafkaProducers: KafkaProducers,
    private val oppgaveService: OppgaveService
) {
    fun hentManuellOppgaver(oppgaveId: Int): ManuellOppgaveDTO? =
        database.hentManuellOppgaver(oppgaveId)

    fun finnesOppgave(oppgaveId: Int): Boolean =
            database.finnesOppgave(oppgaveId)

    fun finnesSykmelding(sykmeldingId: String): Boolean =
        database.finnesSykmelding(sykmeldingId)

    fun erApprecSendt(oppgaveId: Int): Boolean =
            database.erApprecSendt(oppgaveId)

    fun toggleApprecSendt(oppgaveId: Int) =
            database.oppdaterApprecStatus(oppgaveId, true)

    suspend fun ferdigstillManuellBehandling(oppgaveId: Int, enhet: String, veileder: String, accessToken: String, merknader: List<Merknad>?) {
        val validationResult = ValidationResult(Status.OK, emptyList())
        val manuellOppgave = hentManuellOppgave(oppgaveId, accessToken).updateMerknader(merknader)
        val loggingMeta = LoggingMeta(
            mottakId = manuellOppgave.receivedSykmelding.navLogId,
            orgNr = manuellOppgave.receivedSykmelding.legekontorOrgNr,
            msgId = manuellOppgave.receivedSykmelding.msgId,
            sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id
        )

        incrementCounters(validationResult, manuellOppgave)

        sendReceivedSykmelding(manuellOppgave.receivedSykmelding, loggingMeta)

        if (!erApprecSendt(oppgaveId)) {
            /**
             * Fallback for å sende apprec for oppgaver hvor apprec ikke har blitt sendt
             * Tidligere ble apprec sendt ved ferdigstilling, mens det nå blir sendt ved mottak i manuell
             * Frem til alle gamle oppgaver er ferdigstilt er vi nødt til å sjekke
              */

            sendApprec(oppgaveId, manuellOppgave.apprec, loggingMeta)
        }

        sendToSyfoService(manuellOppgave.receivedSykmelding, loggingMeta)
        oppgaveService.ferdigstillOppgave(manuellOppgave, loggingMeta, enhet, veileder)

        if (skalOppretteOppfolgingsOppgave(manuellOppgave)) {
            oppgaveService.opprettOppfoligingsOppgave(manuellOppgave, enhet, veileder, loggingMeta)
        }

        if (manuellOppgave.opprinneligValidationResult == null) {
            log.info("Mangler opprinnelig validation result, oppdaterer ved ferdigstilling av oppgaveId $oppgaveId")
            database.oppdaterManuellOppgaveUtenOpprinneligValidationResult(
                oppgaveId = oppgaveId,
                receivedSykmelding = manuellOppgave.receivedSykmelding,
                validationResult = validationResult,
                opprinneligValidationResult = manuellOppgave.validationResult
            )
        } else {
            database.oppdaterManuellOppgave(oppgaveId, manuellOppgave.receivedSykmelding, validationResult)
        }
        FERDIGSTILT_OPPGAVE_COUNTER.inc()
    }

    private fun skalOppretteOppfolgingsOppgave(manuellOppgave: ManuellOppgaveKomplett): Boolean {
        return manuellOppgave.receivedSykmelding.merknader?.any {
            it.type == "UGYLDIG_TILBAKEDATERING" ||
                    it.type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER"
        } ?: false
    }

    private fun incrementCounters(validationResult: ValidationResult, manuellOppgaveWithMerknad: ManuellOppgaveKomplett) {
        validationResult.ruleHits.onEach { RULE_HIT_COUNTER.labels(it.ruleName).inc() }
        RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()
        manuellOppgaveWithMerknad.receivedSykmelding.merknader?.onEach { MERKNAD_COUNTER.labels(it.type).inc() }
    }

    private suspend fun hentManuellOppgave(oppgaveId: Int, accessToken: String): ManuellOppgaveKomplett {
        val manuellOppgave = database.hentKomplettManuellOppgave(oppgaveId).firstOrNull()
        if (manuellOppgave == null) {
            log.error("Fant ikke oppgave med id $oppgaveId")
            throw OppgaveNotFoundException("Fant ikke oppgave med id $oppgaveId")
        }
        val harTilgangTilOppgave =
            syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                    accessToken = accessToken,
                    personFnr = manuellOppgave.receivedSykmelding.personNrPasient
            )?.harTilgang
        if (harTilgangTilOppgave != true) {
            throw ForbiddenException()
        }
        return manuellOppgave
    }

    fun sendToSyfoService(receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta) {
        val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
            setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
            setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
        }
        val fellesformat = fellesformatUnmarshaller.unmarshal(
                StringReader(receivedSykmelding.fellesformat)) as XMLEIFellesformat

        notifySyfoService(
            syfoserviceProducer = kafkaProducers.kafkaSyfoserviceProducer,
            ediLoggId = receivedSykmelding.navLogId,
            sykmeldingId = receivedSykmelding.sykmelding.id,
            msgId = receivedSykmelding.msgId,
            healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat),
            loggingMeta = loggingMeta
        )
    }

    fun sendApprec(oppgaveId: Int, apprec: Apprec, loggingMeta: LoggingMeta) {
        try {
            kafkaProducers.kafkaApprecProducer.producer.send(ProducerRecord(kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic, apprec)).get()
            log.info("Apprec kvittering sent til kafka topic {} {}", kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic, loggingMeta)
            toggleApprecSendt(oppgaveId)
        } catch (ex: Exception) {
            log.error("Failed to send apprec {}", loggingMeta)
            throw ex
        }
    }

    fun lagOppdatertApprec(manuellOppgave: ManuellOppgave): Apprec =
        Apprec(
            ediloggid = manuellOppgave.apprec.ediloggid,
            msgId = manuellOppgave.apprec.msgId,
            msgTypeVerdi = manuellOppgave.apprec.msgTypeVerdi,
            msgTypeBeskrivelse = manuellOppgave.apprec.msgTypeBeskrivelse,
            genDate = manuellOppgave.apprec.genDate,
            msgGenDate = manuellOppgave.apprec.msgGenDate,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = "Sykmeldingen er til manuell vurdering for tilbakedatering",
            senderOrganisasjon = manuellOppgave.apprec.senderOrganisasjon,
            mottakerOrganisasjon = manuellOppgave.apprec.mottakerOrganisasjon,
            validationResult = manuellOppgave.validationResult
        )

    fun sendReceivedSykmelding(receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta) {
        val topic = kafkaProducers.kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic
        try {
            kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(
                ProducerRecord(
                    topic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding
                )
            ).get()
            log.info("Sendt sykmelding {} to topic {} {}", receivedSykmelding.sykmelding.id, topic, loggingMeta)
        } catch (ex: Exception) {
            log.error("Failed to send sykmelding {} to topic {} {}", receivedSykmelding.sykmelding.id, topic, loggingMeta)
            throw ex
        }
    }
}
