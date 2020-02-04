package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
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
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
fun Route.sendVurderingManuellOppgave(
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
    oppgaveClient: OppgaveClient,
    syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    route("/api/v1") {
        put("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info("Mottok eit kall til /api/v1/vurderingmanuelloppgave med oppgaveid, {}", oppgaveId)

            val accessToken = getAccessTokenFromAuthHeader(call.request)
            log.info("accessToken is mapped OK")

            val validationResult: ValidationResult? = call.receive()
            log.info("validationResult is mapped OK")

            when {
                validationResult == null -> {
                    log.info("Mangler validationResult")
                    call.respond(HttpStatusCode.BadRequest)
                }
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    if (manuellOppgaveService.oppdaterValidationResults(oppgaveId, validationResult) > 0) {
                        val manuellOppgave = manuellOppgaveService.hentKomplettManuellOppgave(oppgaveId)
                        log.info("hentKomplettManuellOppgave is done")

                        if (manuellOppgave != null) {
                            val loggingMeta = LoggingMeta(
                                mottakId = manuellOppgave.receivedSykmelding.navLogId,
                                orgNr = manuellOppgave.receivedSykmelding.legekontorOrgNr,
                                msgId = manuellOppgave.receivedSykmelding.msgId,
                                sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id
                            )

                            val pasientFnr = manuellOppgave.receivedSykmelding.personNrPasient

                            val harTilgangTilOppgave =
                                syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(
                                    accessToken,
                                    pasientFnr
                                )?.harTilgang
                            if (harTilgangTilOppgave != null && harTilgangTilOppgave) {
                                when (manuellOppgave.validationResult.status) {
                                    Status.INVALID -> {
                                        handleManuellOppgaveInvalid(
                                            manuellOppgave,
                                            sm2013ApprecTopicName,
                                            kafkaproducerApprec,
                                            sm2013InvalidHandlingTopic,
                                            kafkaproducerreceivedSykmelding,
                                            sm2013BehandlingsUtfallToipic,
                                            kafkaproducervalidationResult,
                                            loggingMeta,
                                            oppgaveClient
                                        )
                                        call.respond(HttpStatusCode.NoContent)
                                    }
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
                                            oppgaveClient
                                        )
                                        call.respond(HttpStatusCode.NoContent)
                                    }
                                    else -> {
                                        call.respond(HttpStatusCode.BadRequest)
                                        log.error(
                                            "Syfosmmanuell sendt ein ugyldig validationResult.status, {}, {}",
                                            oppgaveId, fields(loggingMeta)
                                        )
                                    }
                                }
                            } else {
                                log.warn("Veileder har ikkje tilgang")
                                call.respond(HttpStatusCode.Unauthorized)
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

fun ferdigStillOppgave(manuellOppgave: ManuellOppgaveKomplett, oppgaveVersjon: Int) = FerdigStillOppgave(
        versjon = oppgaveVersjon,
        id = manuellOppgave.oppgaveid,
        status = OppgaveStatus.FERDIGSTILT
)
