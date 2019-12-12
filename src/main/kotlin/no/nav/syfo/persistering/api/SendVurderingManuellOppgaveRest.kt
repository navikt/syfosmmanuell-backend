package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.FerdigStillOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.OppgaveStatus
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
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
        put("/vurderingmanuelloppgave/{manuelloppgaveId}") {
            val manuellOppgaveId = call.parameters["manuelloppgaveId"]!!
            log.info("Recived call to /api/v1/vurderingmanuelloppgave")

            val validationResult: ValidationResult = call.receive()

            if (manuellOppgaveService.oppdaterValidationResuts(manuellOppgaveId, validationResult) > 0) {
                val manuellOppgave = manuellOppgaveService.hentKomplettManuellOppgave(manuellOppgaveId)

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
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(manuellOppgave.receivedSykmelding.fellesformat)) as XMLEIFellesformat

    notifySyfoService(
        session = session,
        receiptProducer = syfoserviceProducer,
        ediLoggId = manuellOppgave.receivedSykmelding.navLogId,
        sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id,
        msgId = manuellOppgave.receivedSykmelding.msgId,
        healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat))
    log.info("Message send to syfoService {}, {}", syfoserviceQueueName, fields(loggingMeta))

    kafkaproducerreceivedSykmelding.send(ProducerRecord(
        sm2013AutomaticHandlingTopic,
        manuellOppgave.receivedSykmelding.sykmelding.id,
        manuellOppgave.receivedSykmelding))
    log.info("Message send to kafka {}, {}", sm2013AutomaticHandlingTopic, fields(loggingMeta))

    val ferdigStillOppgave = createFerdigStillOppgave(manuellOppgave)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, manuellOppgave.receivedSykmelding.msgId)
    log.info(
        "Ferdigstilt oppgave med {}, {} {}",
        StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
        StructuredArguments.keyValue("tildeltEnhetsnr", manuellOppgave.behandlendeEnhet),
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
}

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

    kafkaproducerreceivedSykmelding.send(ProducerRecord(
        sm2013InvalidHandlingTopic,
        manuellOppgave.receivedSykmelding.sykmelding.id,
        manuellOppgave.receivedSykmelding)
    )
    log.info("Message send to kafka {}, {}", sm2013InvalidHandlingTopic, fields(loggingMeta))

    sendValidationResult(
        manuellOppgave.validationResult,
        kafkaproducervalidationResult,
        sm2013BehandlingsUtfallToipic,
        manuellOppgave.receivedSykmelding,
        loggingMeta)

    val ferdigStillOppgave = createFerdigStillOppgave(manuellOppgave)

    val oppgaveResponse = oppgaveClient.ferdigStillOppgave(ferdigStillOppgave, manuellOppgave.receivedSykmelding.msgId)
    log.info(
        "Ferdigstilt oppgave med {}, {} {}",
        StructuredArguments.keyValue("oppgaveId", oppgaveResponse.id),
        StructuredArguments.keyValue("tildeltEnhetsnr", manuellOppgave.behandlendeEnhet),
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

fun createFerdigStillOppgave(manuellOppgave: ManuellOppgaveKomplett) = FerdigStillOppgave(
        versjon = 1,
        id = manuellOppgave.oppgaveid,
        status = OppgaveStatus.FERDIGSTILT
)
