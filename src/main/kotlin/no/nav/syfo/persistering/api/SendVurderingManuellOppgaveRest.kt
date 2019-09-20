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
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun Routing.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    sm2013ApprecTopicName: String
) {
    route("/api/v1") {
        post("/vurderingmanuelloppgave/{manuelloppgaveId}") {
            val manuellOppgaveId = call.parameters["manuelloppgaveId"]!!
            log.info("Recived call to /api/v1/vurderingmanuelloppgave")

            val validationResult: ValidationResult = call.receive()

            if (manuellOppgaveService.oppdaterValidationResuts(manuellOppgaveId, validationResult) > 0) {
                log.info("Oppdatering av validation results, gikk ok")
                val manuellOppgave = manuellOppgaveService.hentKomplettManuellOppgave(manuellOppgaveId)
                log.info("Henting av manuell komplett manuell oppgave gikk ok")
                // TODO send event update to modia

                if (manuellOppgave != null) {
                    if (manuellOppgave.validationResult.status == Status.INVALID) {

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

                        val loggingMeta = LoggingMeta(
                            mottakId = manuellOppgave.receivedSykmelding.navLogId,
                            orgNr = manuellOppgave.receivedSykmelding.legekontorOrgNr,
                            msgId = manuellOppgave.receivedSykmelding.msgId,
                            sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id
                        )

                        log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopicName, fields(loggingMeta))
                    }
                }
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

fun sendReceipt(
    apprec: Apprec,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    kafkaproducerApprec.send(ProducerRecord(sm2013ApprecTopic, apprec))
}
