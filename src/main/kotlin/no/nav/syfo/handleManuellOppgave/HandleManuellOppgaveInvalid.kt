package no.nav.syfo.handleManuellOppgave

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.persistering.api.ferdigStillOppgave
import no.nav.syfo.persistering.api.sendReceipt
import no.nav.syfo.persistering.api.sendValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleManuellOppgaveInvalid(
        manuellOppgave: ManuellOppgaveKomplett,
        sm2013ApprecTopicName: String,
        kafkaproducerApprec: KafkaProducer<String, Apprec>,
        sm2013InvalidHandlingTopic: String,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        sm2013BehandlingsUtfallTopic: String,
        kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
        loggingMeta: LoggingMeta,
        oppgaveClient: OppgaveClient
) {

    kafkaproducerreceivedSykmelding.send(
        ProducerRecord(
            sm2013InvalidHandlingTopic,
            manuellOppgave.receivedSykmelding.sykmelding.id,
            manuellOppgave.receivedSykmelding)
    )
    log.info("Melding sendt til kafka topic {}, {}", sm2013InvalidHandlingTopic, fields(loggingMeta))

    sendValidationResult(
        manuellOppgave.validationResult,
        kafkaproducervalidationResult,
        sm2013BehandlingsUtfallTopic,
        manuellOppgave.receivedSykmelding,
        loggingMeta)

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
        apprecStatus = ApprecStatus.AVVIST,
        tekstTilSykmelder = null,
        senderOrganisasjon = manuellOppgave.apprec.senderOrganisasjon,
        mottakerOrganisasjon = manuellOppgave.apprec.mottakerOrganisasjon,
        validationResult = manuellOppgave.validationResult
    )

    sendReceipt(apprec, sm2013ApprecTopicName, kafkaproducerApprec)
    log.info("Apprec kvittering sent til kafka topic {}, {}", sm2013ApprecTopicName,
        fields(loggingMeta)
    )
}
