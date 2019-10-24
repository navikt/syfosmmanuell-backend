package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.put
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun Routing.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    sm2013ApprecTopicName: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013AutomaticHandlingTopic: String,
    sm2013InvalidHandlingTopic: String,
    sm2013BehandlingsUtfallToipic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>
) {
    route("/api/v1") {
        put("/vurderingmanuelloppgave/{manuelloppgaveId}") {
            val manuellOppgaveId = call.parameters["manuelloppgaveId"]!!
            log.info("Recived call to /api/v1/vurderingmanuelloppgave")

            val validationResult: ValidationResult = call.receive()

            if (manuellOppgaveService.oppdaterValidationResuts(manuellOppgaveId, validationResult) > 0) {
                val manuellOppgave = manuellOppgaveService.hentKomplettManuellOppgave(manuellOppgaveId)
                // TODO send event update to modia

                if (manuellOppgave != null) {
                    val loggingMeta = LoggingMeta(
                        mottakId = manuellOppgave.receivedSykmelding.navLogId,
                        orgNr = manuellOppgave.receivedSykmelding.legekontorOrgNr,
                        msgId = manuellOppgave.receivedSykmelding.msgId,
                        sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id
                    )

                    when (manuellOppgave.validationResult.status) {
                        Status.INVALID -> { handleManuellOppgaveInvalid(
                            manuellOppgave,
                            sm2013ApprecTopicName,
                            kafkaproducerApprec,
                            sm2013InvalidHandlingTopic,
                            kafkaproducerreceivedSykmelding,
                            sm2013BehandlingsUtfallToipic,
                            kafkaproducervalidationResult,
                            loggingMeta)
                            call.respond(HttpStatusCode.NoContent) }
                        Status.OK -> {
                            handleManuellOppgaveOk(
                                manuellOppgave,
                                sm2013AutomaticHandlingTopic,
                                kafkaproducerreceivedSykmelding,
                                loggingMeta)
                            call.respond(HttpStatusCode.NoContent) }
                        else -> { call.respond(HttpStatusCode.BadRequest)
                            log.error("Syfosmmanuell sendt ein ugyldig validationResult.status, {}, {}",
                                manuellOppgaveId, fields(loggingMeta))
                            }
                    }
                } else {
                    log.warn("Henting av komplettManuellOppgave returente null manuelloppgaveid, {}", manuellOppgaveId)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            } else {
                log.error("Oppdatering av oppdaterValidationResuts feilet manuelloppgaveid, {}", manuellOppgaveId)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

fun handleManuellOppgaveOk(
    manuellOppgave: ManuellOppgave,
    sm2013AutomaticHandlingTopic: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    loggingMeta: LoggingMeta
) {
    kafkaproducerreceivedSykmelding.send(ProducerRecord(
        sm2013AutomaticHandlingTopic,
        manuellOppgave.receivedSykmelding.sykmelding.id,
        manuellOppgave.receivedSykmelding))

        log.info("Message send to kafka {}, {}", sm2013AutomaticHandlingTopic, fields(loggingMeta))
}

fun handleManuellOppgaveInvalid(
    manuellOppgave: ManuellOppgave,
    sm2013ApprecTopicName: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    sm2013InvalidHandlingTopic: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013BehandlingsUtfallToipic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    loggingMeta: LoggingMeta
) {

    kafkaproducerreceivedSykmelding.send(ProducerRecord(
        sm2013InvalidHandlingTopic,
        manuellOppgave.receivedSykmelding.sykmelding.id,
        manuellOppgave.receivedSykmelding)
    )
    sendValidationResult(
        manuellOppgave.validationResult,
        kafkaproducervalidationResult,
        sm2013BehandlingsUtfallToipic,
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
    log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopicName, fields(loggingMeta))
}

fun sendReceipt(
    apprec: Apprec,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    kafkaproducerApprec.send(ProducerRecord(sm2013ApprecTopic, apprec))
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallToipic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {

    kafkaproducervalidationResult.send(
        ProducerRecord(sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult)
    )
    log.info("Validation results send to kafka {}, {}", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
}
