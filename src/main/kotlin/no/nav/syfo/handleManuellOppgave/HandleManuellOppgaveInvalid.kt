package no.nav.syfo.handleManuellOppgave

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
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
    sm2013BehandlingsUtfallToipic: String,
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
    log.info("Message send to kafka {}, {}", sm2013InvalidHandlingTopic, StructuredArguments.fields(loggingMeta))

    sendValidationResult(
        manuellOppgave.validationResult,
        kafkaproducervalidationResult,
        sm2013BehandlingsUtfallToipic,
        manuellOppgave.receivedSykmelding,
        loggingMeta)

    val ferdigStillOppgave = ferdigStillOppgave(manuellOppgave)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, manuellOppgave.receivedSykmelding.msgId)
    log.info(
        "Ferdigstilt oppgave med {}, {} {}",
        StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
        StructuredArguments.keyValue("tildeltEnhetsnr", manuellOppgave.behandlendeEnhet),
        StructuredArguments.fields(loggingMeta)
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
    log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopicName,
        StructuredArguments.fields(loggingMeta)
    )
}
