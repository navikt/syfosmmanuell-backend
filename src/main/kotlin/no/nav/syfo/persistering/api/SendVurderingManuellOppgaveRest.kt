package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
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
    sm2013InvalidHandlingTopic: String
) {
    route("/api/v1") {
        post("/vurderingmanuelloppgave/{manuelloppgaveId}") {
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

                    if (manuellOppgave.validationResult.status == Status.INVALID) {
                        handleManuellOppgaveInvalid(
                            manuellOppgave,
                            sm2013ApprecTopicName,
                            kafkaproducerApprec,
                            sm2013InvalidHandlingTopic,
                            kafkaproducerreceivedSykmelding,
                            loggingMeta)
                    } else {
                        kafkaproducerreceivedSykmelding.send(ProducerRecord(
                            sm2013AutomaticHandlingTopic,
                            manuellOppgave.receivedSykmelding.sykmelding.id,
                            manuellOppgave.receivedSykmelding)
                        )
                        log.info("Message send to kafka {}, {}", sm2013AutomaticHandlingTopic, fields(loggingMeta))
                    }
                    call.respond(HttpStatusCode.OK)
                } else {
                    log.warn("Henting av komplettManuellOppgave returente null")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

fun handleManuellOppgaveInvalid(
    manuellOppgave: ManuellOppgave,
    sm2013ApprecTopicName: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    sm2013InvalidHandlingTopic: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    loggingMeta: LoggingMeta
) {

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

    kafkaproducerreceivedSykmelding.send(ProducerRecord(
        sm2013InvalidHandlingTopic,
        manuellOppgave.receivedSykmelding.sykmelding.id,
        manuellOppgave.receivedSykmelding)
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
