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
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.handleManuellOppgave.handleManuellOppgaveInvalid
import no.nav.syfo.handleManuellOppgave.handleManuellOppgaveOk
import no.nav.syfo.log
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
fun Route.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    kafkaApprecProducer: KafkaProducers.KafkaApprecProducer,
    kafkaRecievedSykmeldingProducer: KafkaProducers.KafkaRecievedSykmeldingProducer,
    kafkaValidationResultProducer: KafkaProducers.KafkaValidationResultProducer,
    session: Session,
    syfoserviceProducer: MessageProducer,
    oppgaveService: OppgaveService,
    syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    route("/api/v1") {
        put("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info(
                "Mottok eit kall til /api/v1/vurderingmanuelloppgave med {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId)
            )

            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val validationResult: ValidationResult = call.receive()

            when (accessToken) {
                null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val manuellOppgave = manuellOppgaveService.hentKomplettManuellOppgave(oppgaveId)
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
                        if (harTilgangTilOppgave == true) {
                            validationResult.ruleHits.onEach { RULE_HIT_COUNTER.labels(it.ruleName).inc() }
                            RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()
                            when (validationResult.status) {
                                Status.INVALID -> {
                                    if (manuellOppgaveService.oppdaterValidationResults(
                                                    oppgaveId,
                                                    validationResult
                                            ) > 0
                                    ) {
                                        handleManuellOppgaveInvalid(
                                                manuellOppgave,
                                                kafkaApprecProducer.sm2013ApprecTopic,
                                                kafkaApprecProducer.producer,
                                                kafkaRecievedSykmeldingProducer.sm2013InvalidHandlingTopic,
                                                kafkaRecievedSykmeldingProducer.producer,
                                                kafkaRecievedSykmeldingProducer.sm2013BehandlingsUtfallTopic,
                                                kafkaValidationResultProducer.producer,
                                                loggingMeta,
                                                oppgaveService,
                                                validationResult
                                        )
                                        call.respond(HttpStatusCode.NoContent)
                                    } else {
                                        log.error(
                                                "Oppdatering av oppdaterValidationResuts feilet {}",
                                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                        )
                                        call.respond(HttpStatusCode.InternalServerError)
                                    }
                                }
                                Status.OK -> {
                                    if (manuellOppgaveService.oppdaterValidationResults(
                                                    oppgaveId,
                                                    validationResult
                                            ) > 0
                                    ) {
                                        handleManuellOppgaveOk(
                                                manuellOppgave,
                                                kafkaRecievedSykmeldingProducer.sm2013AutomaticHandlingTopic,
                                                kafkaRecievedSykmeldingProducer.producer,
                                                loggingMeta,
                                                kafkaValidationResultProducer.syfoserviceQueueName,
                                                session,
                                                syfoserviceProducer,
                                                kafkaApprecProducer.sm2013ApprecTopic,
                                                kafkaApprecProducer.producer,
                                                oppgaveService
                                        )
                                        call.respond(HttpStatusCode.NoContent)
                                    } else {
                                        log.error(
                                                "Oppdatering av oppdaterValidationResuts feilet {}",
                                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                                        )
                                        call.respond(HttpStatusCode.InternalServerError)
                                    }
                                }
                                else -> {
                                    call.respond(HttpStatusCode.BadRequest)
                                    log.error(
                                            "Syfosmmanuell sendt ein ugyldig validationResult.status,{}  {}, {}",
                                            validationResult.status.name,
                                            StructuredArguments.keyValue("oppgaveId", oppgaveId), fields(loggingMeta)
                                    )
                                }
                            }
                        } else {
                            log.warn(
                                    "Veileder har ikkje tilgang, {}, {}",
                                    StructuredArguments.keyValue("oppgaveId", oppgaveId), fields(loggingMeta)
                            )
                            call.respond(HttpStatusCode.Unauthorized)
                        }
                    } else {
                        log.warn(
                                "Henting av komplettManuellOppgave returente null {}",
                                StructuredArguments.keyValue("oppgaveId", oppgaveId)
                        )
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
    try {
        kafkaproducerApprec.send(ProducerRecord(sm2013ApprecTopic, apprec)).get()
        log.info("Apprec kvittering sent til kafka topic {}", sm2013ApprecTopic)
    } catch (ex: Exception) {
        log.error("Failed to send apprec")
        throw ex
    }
}

fun sendReceivedSykmelding(sm2013Topic: String, receivedSykmelding: ReceivedSykmelding, kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>) {
    try {
        kafkaproducerreceivedSykmelding.send(
                ProducerRecord(
                        sm2013Topic,
                        receivedSykmelding.sykmelding.id,
                        receivedSykmelding)
        ).get()
        log.info("Sendt sykmelding {} to topic {}", receivedSykmelding.sykmelding.id, sm2013Topic)
    } catch (ex: Exception) {
        log.error("Failed to send sykmelding {} to topic {}", receivedSykmelding.sykmelding.id, sm2013Topic)
        throw ex
    }
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {
    try {
        kafkaproducervalidationResult.send(
                ProducerRecord(sm2013BehandlingsUtfallTopic, receivedSykmelding.sykmelding.id, validationResult)
        ).get()
        log.info("Valideringsreultat sendt til kafka {}, {}", sm2013BehandlingsUtfallTopic, fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("Failed to send validation result for sykmelding {} to topic {}", receivedSykmelding.sykmelding.id, sm2013BehandlingsUtfallTopic)
        throw ex
    }
}
