package no.nav.syfo.handleManuellOppgave

import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.metrics.FERDIGSTILT_OPPGAVE_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.api.sendReceipt
import no.nav.syfo.persistering.api.sendReceivedSykmelding
import no.nav.syfo.persistering.api.sendValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer

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
    oppgaveService: OppgaveService,
    validationResult: ValidationResult
) {
    sendReceivedSykmelding(sm2013InvalidHandlingTopic, manuellOppgave.receivedSykmelding, kafkaproducerreceivedSykmelding)

    sendValidationResult(
        validationResult,
        kafkaproducervalidationResult,
        sm2013BehandlingsUtfallTopic,
        manuellOppgave.receivedSykmelding,
        loggingMeta)

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

    oppgaveService.ferdigstillOppgave(manuellOppgave, loggingMeta)

    FERDIGSTILT_OPPGAVE_COUNTER.inc()
}
