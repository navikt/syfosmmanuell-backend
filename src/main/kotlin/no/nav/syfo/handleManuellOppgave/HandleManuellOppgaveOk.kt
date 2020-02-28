package no.nav.syfo.handleManuellOppgave

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.metrics.FERDIGSTILT_OPPGAVE_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.api.ferdigStillOppgave
import no.nav.syfo.persistering.api.sendReceipt
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleManuellOppgaveOk(
    manuellOppgave: ManuellOppgaveKomplett,
    sm2013AutomaticHandlingTopic: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    loggingMeta: LoggingMeta,
    syfoserviceQueueName: String,
    session: Session,
    syfoserviceProducer: MessageProducer,
    sm2013ApprecTopicName: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    oppgaveClient: OppgaveClient
) {
    val fellesformat = fellesformatUnmarshaller.unmarshal(
        StringReader(manuellOppgave.receivedSykmelding.fellesformat)) as XMLEIFellesformat

    // TODO remove notifySyfoService, when we no longer uses syfoService app to show sykmeldinger
    notifySyfoService(
        session = session,
        receiptProducer = syfoserviceProducer,
        ediLoggId = manuellOppgave.receivedSykmelding.navLogId,
        sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id,
        msgId = manuellOppgave.receivedSykmelding.msgId,
        healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    )
    log.info("Melding sendt til syfoService kø {}, {}", syfoserviceQueueName, fields(loggingMeta))

    kafkaproducerreceivedSykmelding.send(
        ProducerRecord(
            sm2013AutomaticHandlingTopic,
            manuellOppgave.receivedSykmelding.sykmelding.id,
            manuellOppgave.receivedSykmelding)
    )
    log.info("Melding sendt til kafka topic {}, {}", sm2013AutomaticHandlingTopic, fields(loggingMeta))

    val oppgaveVersjon = oppgaveClient.hentOppgave(manuellOppgave.oppgaveid, manuellOppgave.receivedSykmelding.msgId).versjon

    val ferdigStillOppgave = ferdigStillOppgave(manuellOppgave, oppgaveVersjon)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, manuellOppgave.receivedSykmelding.msgId)
    log.info(
        "Ferdigstilter oppgave med {}, {}",
        keyValue("oppgaveId", oppgaveResponse.id),
        fields(loggingMeta)
    )

    val apprec = Apprec(
        ediloggid = manuellOppgave.apprec.ediloggid,
        msgId = manuellOppgave.apprec.msgId,
        msgTypeVerdi = manuellOppgave.apprec.msgTypeVerdi,
        msgTypeBeskrivelse = manuellOppgave.apprec.msgTypeBeskrivelse,
        genDate = manuellOppgave.apprec.genDate,
        apprecStatus = ApprecStatus.OK,
        tekstTilSykmelder = null,
        senderOrganisasjon = manuellOppgave.apprec.senderOrganisasjon,
        mottakerOrganisasjon = manuellOppgave.apprec.mottakerOrganisasjon,
        validationResult = null
    )

    sendReceipt(apprec, sm2013ApprecTopicName, kafkaproducerApprec)
    log.info("Apprec sendt til kafka topic {}, {}", sm2013ApprecTopicName,
        fields(loggingMeta)
    )

    FERDIGSTILT_OPPGAVE_COUNTER.inc()
}
