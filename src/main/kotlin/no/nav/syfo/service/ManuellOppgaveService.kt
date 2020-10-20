package no.nav.syfo.service

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.ws.rs.ForbiddenException
import javax.xml.bind.Unmarshaller
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaver
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.log
import no.nav.syfo.metrics.FERDIGSTILT_OPPGAVE_COUNTER
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.oppdaterValidationResultsOgApprec
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
    fun hentManuellOppgaver(oppgaveId: Int): List<ManuellOppgaveDTO> =
        database.hentManuellOppgaver(oppgaveId)

    suspend fun ferdigstillManuellBehandling(oppgaveId: Int, enhet: String, validationResult: ValidationResult, accessToken: String) {
        val manuellOppgave = hentManuellOppgave(oppgaveId, accessToken)
        val loggingMeta = LoggingMeta(
            mottakId = manuellOppgave.receivedSykmelding.navLogId,
            orgNr = manuellOppgave.receivedSykmelding.legekontorOrgNr,
            msgId = manuellOppgave.receivedSykmelding.msgId,
            sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id
        )

        validationResult.ruleHits.onEach { RULE_HIT_COUNTER.labels(it.ruleName).inc() }
        RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()

        sendReceivedSykmelding(kafkaProducers.kafkaRecievedSykmeldingProducer, manuellOppgave.receivedSykmelding, validationResult.status, loggingMeta)

        when (validationResult.status) {
            Status.OK -> sendToSyfoService(manuellOppgave, loggingMeta)
            Status.INVALID -> sendValidationResult(validationResult, manuellOppgave.receivedSykmelding, loggingMeta)
            else -> throw IllegalArgumentException("Validation result must be OK or INVALID")
        }

        val oppdatertApprec = lagOppdatertApprec(manuellOppgave, validationResult)
        sendApprec(oppdatertApprec, loggingMeta)
        oppgaveService.ferdigstillOppgave(manuellOppgave, loggingMeta, enhet)
        oppdaterValidationResultsOgApprec(oppgaveId, validationResult, oppdatertApprec)
        FERDIGSTILT_OPPGAVE_COUNTER.inc()
    }

    private suspend fun hentManuellOppgave(oppgaveId: Int, accessToken: String): ManuellOppgaveKomplett {
        val manuellOppgave = hentKomplettManuellOppgave(oppgaveId)
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

    private fun oppdaterValidationResultsOgApprec(oppgaveId: Int, validationResult: ValidationResult, apprec: Apprec): Int =
        database.oppdaterValidationResultsOgApprec(oppgaveId, validationResult, apprec)

    private fun hentKomplettManuellOppgave(oppgaveId: Int): ManuellOppgaveKomplett? =
        database.hentKomplettManuellOppgave(oppgaveId).firstOrNull()

    private fun sendToSyfoService(manuellOppgave: ManuellOppgaveKomplett, loggingMeta: LoggingMeta) {
        val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
            setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
            setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
        }
        val fellesformat = fellesformatUnmarshaller.unmarshal(
                StringReader(manuellOppgave.receivedSykmelding.fellesformat)) as XMLEIFellesformat

        notifySyfoService(
            syfoserviceProducer = kafkaProducers.kafkaSyfoserviceProducer,
            ediLoggId = manuellOppgave.receivedSykmelding.navLogId,
            sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id,
            msgId = manuellOppgave.receivedSykmelding.msgId,
            healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat),
            loggingMeta = loggingMeta
        )
    }

    private fun sendApprec(apprec: Apprec, loggingMeta: LoggingMeta) {
        try {
            kafkaProducers.kafkaApprecProducer.producer.send(ProducerRecord(kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic, apprec)).get()
            log.info("Apprec kvittering sent til kafka topic {} {}", kafkaProducers.kafkaApprecProducer.sm2013ApprecTopic, loggingMeta)
        } catch (ex: Exception) {
            log.error("Failed to send apprec {}", loggingMeta)
            throw ex
        }
    }

    private fun lagOppdatertApprec(manuellOppgave: ManuellOppgaveKomplett, validationResult: ValidationResult): Apprec =
        Apprec(
            ediloggid = manuellOppgave.apprec.ediloggid,
            msgId = manuellOppgave.apprec.msgId,
            msgTypeVerdi = manuellOppgave.apprec.msgTypeVerdi,
            msgTypeBeskrivelse = manuellOppgave.apprec.msgTypeBeskrivelse,
            genDate = manuellOppgave.apprec.genDate,
            apprecStatus = getApprecStatus(validationResult.status),
            tekstTilSykmelder = null,
            senderOrganisasjon = manuellOppgave.apprec.senderOrganisasjon,
            mottakerOrganisasjon = manuellOppgave.apprec.mottakerOrganisasjon,
            validationResult = if (validationResult.status == Status.OK) null else validationResult
        )

    private fun getApprecStatus(status: Status): ApprecStatus {
        return when (status) {
            Status.OK -> ApprecStatus.OK
            Status.INVALID -> ApprecStatus.AVVIST
            else -> throw IllegalArgumentException("Validation result must be OK or INVALID")
        }
    }

    private fun sendReceivedSykmelding(kafkaProducer: KafkaProducers.KafkaRecievedSykmeldingProducer, receivedSykmelding: ReceivedSykmelding, status: Status, loggingMeta: LoggingMeta) {
        val topic = getTopic(status)
        try {
            kafkaProducer.producer.send(
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

    fun sendValidationResult(
        validationResult: ValidationResult,
        receivedSykmelding: ReceivedSykmelding,
        loggingMeta: LoggingMeta
    ) {
        val topic = kafkaProducers.kafkaValidationResultProducer.sm2013BehandlingsUtfallTopic
        try {
            kafkaProducers.kafkaValidationResultProducer.producer.send(
                ProducerRecord(topic, receivedSykmelding.sykmelding.id, validationResult)
            ).get()
            log.info("Valideringsreultat sendt til kafka {}, {}", topic, StructuredArguments.fields(loggingMeta))
        } catch (ex: Exception) {
            log.error("Failed to send validation result for sykmelding {} to topic {} {}", receivedSykmelding.sykmelding.id, topic, loggingMeta)
            throw ex
        }
    }

    private fun getTopic(status: Status): String {
        return when (status) {
            Status.OK -> kafkaProducers.kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic
            Status.INVALID -> kafkaProducers.kafkaRecievedSykmeldingProducer.sm2013InvalidHandlingTopic
            else -> throw IllegalArgumentException("Validation result must be OK or INVALID")
        }
    }
}
