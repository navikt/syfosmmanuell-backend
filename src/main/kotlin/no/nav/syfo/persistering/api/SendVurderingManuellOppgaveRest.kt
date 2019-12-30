package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.handleManuellOppgave.handleManuellOppgaveInvalid
import no.nav.syfo.handleManuellOppgave.handleManuellOppgaveOk
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.FerdigStillOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
fun Routing.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    sm2013ApprecTopicName: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013AutomaticHandlingTopic: String,
    sm2013InvalidHandlingTopic: String,
    sm2013BehandlingsUtfallToipic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoserviceQueueName: String,
    session: Session,
    syfoserviceProducer: MessageProducer,
    oppgaveClient: OppgaveClient
) {
    route("/api/v1") {
        put("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info("Mottok eit kall til /api/v1/vurderingmanuelloppgave med oppgaveid, {}", oppgaveId)

            val validationResult: ValidationResult = call.receive()

            if (manuellOppgaveService.oppdaterValidationResults(oppgaveId, validationResult) > 0) {
                val manuellOppgave = manuellOppgaveService.hentKomplettManuellOppgave(oppgaveId)

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
                            loggingMeta,
                            oppgaveClient)
                            call.respond(HttpStatusCode.NoContent) }
                        Status.OK -> {
                            handleManuellOppgaveOk(
                                manuellOppgave,
                                sm2013AutomaticHandlingTopic,
                                kafkaproducerreceivedSykmelding,
                                loggingMeta,
                                syfoserviceQueueName,
                                session,
                                syfoserviceProducer,
                                sm2013ApprecTopicName,
                                kafkaproducerApprec,
                                oppgaveClient)
                            call.respond(HttpStatusCode.NoContent) }
                        else -> { call.respond(HttpStatusCode.BadRequest)
                            log.error("Syfosmmanuell sendt ein ugyldig validationResult.status, {}, {}",
                                oppgaveId, fields(loggingMeta))
                            }
                    }
                } else {
                    log.warn("Henting av komplettManuellOppgave returente null oppgaveid, {}", oppgaveId)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            } else {
                log.error("Oppdatering av oppdaterValidationResuts feilet oppgaveid, {}", oppgaveId)
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
    log.info("Valideringsreultat sendt til kafka {}, {}", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
}

fun ferdigStillOppgave(manuellOppgave: ManuellOppgaveKomplett) = FerdigStillOppgave(
        versjon = 1,
        id = manuellOppgave.oppgaveid,
        status = OppgaveStatus.FERDIGSTILT
)
