package no.nav.syfo.service

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.UlosteOppgave
import no.nav.syfo.aksessering.db.erApprecSendt
import no.nav.syfo.aksessering.db.finnesOppgave
import no.nav.syfo.aksessering.db.finnesSykmelding
import no.nav.syfo.aksessering.db.getUlosteOppgaver
import no.nav.syfo.aksessering.db.hentKomplettManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgave
import no.nav.syfo.aksessering.db.hentManuellOppgaveForSykmeldingId
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.logger
import no.nav.syfo.metrics.FERDIGSTILT_OPPGAVE_COUNTER
import no.nav.syfo.metrics.MERKNAD_COUNTER
import no.nav.syfo.metrics.RULE_HIT_COUNTER
import no.nav.syfo.metrics.RULE_HIT_STATUS_COUNTER
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ApprecStatus
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveKomplett
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toReceivedSykmeldingWithValidation
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.oppdaterApprecStatus
import no.nav.syfo.persistering.db.oppdaterManuellOppgave
import no.nav.syfo.persistering.db.oppdaterManuellOppgaveUtenOpprinneligValidationResult
import no.nav.syfo.persistering.db.slettOppgave
import no.nav.syfo.persistering.error.OppgaveNotFoundException
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.ProducerRecord

const val PROCESSING_TARGET_HEADER = "processing-target"
const val TSM_PROCESSING_TARGET_VALUE = "tsm"

class ManuellOppgaveService(
    private val database: DatabaseInterface,
    private val istilgangskontrollClient: IstilgangskontrollClient,
    private val kafkaProducers: KafkaProducers,
    private val oppgaveService: OppgaveService,
) {
    suspend fun hentManuellOppgaver(oppgaveId: Int): ManuellOppgaveDTO? =
        database.hentManuellOppgave(oppgaveId)

    suspend fun finnesOppgave(oppgaveId: Int): Boolean = database.finnesOppgave(oppgaveId)

    suspend fun finnesSykmelding(sykmeldingId: String): Boolean =
        database.finnesSykmelding(sykmeldingId)

    suspend fun erApprecSendt(oppgaveId: Int): Boolean = database.erApprecSendt(oppgaveId)

    suspend fun toggleApprecSendt(oppgaveId: Int) = database.oppdaterApprecStatus(oppgaveId, true)

    suspend fun ferdigstillManuellBehandling(
        oppgaveId: Int,
        enhet: String,
        veileder: String,
        accessToken: String,
        merknader: List<Merknad>?
    ) {
        val validationResult =
            ValidationResult(Status.OK, emptyList(), timestamp = OffsetDateTime.now(ZoneOffset.UTC))
        val manuellOppgave = hentManuellOppgave(oppgaveId, accessToken).updateMerknader(merknader)
        val loggingMeta =
            LoggingMeta(
                mottakId = manuellOppgave.receivedSykmelding.navLogId,
                orgNr = manuellOppgave.receivedSykmelding.legekontorOrgNr,
                msgId = manuellOppgave.receivedSykmelding.msgId,
                sykmeldingId = manuellOppgave.receivedSykmelding.sykmelding.id,
            )

        incrementCounters(validationResult, manuellOppgave)

        sendReceivedSykmelding(
            manuellOppgave.receivedSykmelding.toReceivedSykmeldingWithValidation(validationResult),
            loggingMeta,
            isFerdigstiltSykmelding = true
        )

        if (trengerFlereOpplysninger(manuellOppgave)) {
            oppgaveService.endreOppgave(manuellOppgave, loggingMeta)
            return
        }

        if (!erApprecSendt(oppgaveId)) {
            /**
             * Fallback for å sende apprec for oppgaver hvor apprec ikke har blitt sendt Tidligere
             * ble apprec sendt ved ferdigstilling, mens det nå blir sendt ved mottak i manuell Frem
             * til alle gamle oppgaver er ferdigstilt er vi nødt til å sjekke
             */
            sendApprec(oppgaveId, manuellOppgave.apprec, loggingMeta)
        }

        oppgaveService.ferdigstillOppgave(manuellOppgave, loggingMeta, enhet, veileder)

        if (skalOppretteOppfolgingsOppgave(manuellOppgave)) {
            oppgaveService.opprettOppfolgingsOppgave(manuellOppgave, enhet, veileder, loggingMeta)
        }

        if (manuellOppgave.opprinneligValidationResult == null) {
            logger.info(
                "Mangler opprinnelig validation result, oppdaterer ved ferdigstilling av oppgaveId $oppgaveId"
            )
            database.oppdaterManuellOppgaveUtenOpprinneligValidationResult(
                oppgaveId = oppgaveId,
                receivedSykmelding = manuellOppgave.receivedSykmelding,
                validationResult = validationResult,
                opprinneligValidationResult = manuellOppgave.validationResult,
            )
        } else {
            database.oppdaterManuellOppgave(
                oppgaveId,
                manuellOppgave.receivedSykmelding,
                validationResult
            )
        }
        FERDIGSTILT_OPPGAVE_COUNTER.inc()
    }

    private fun trengerFlereOpplysninger(manuellOppgave: ManuellOppgaveKomplett): Boolean {
        return manuellOppgave.receivedSykmelding.merknader?.any {
            it.type == "TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER"
        }
            ?: false
    }

    private fun skalOppretteOppfolgingsOppgave(manuellOppgave: ManuellOppgaveKomplett): Boolean {
        return manuellOppgave.receivedSykmelding.merknader?.any {
            it.type == "UGYLDIG_TILBAKEDATERING"
        }
            ?: false
    }

    private fun incrementCounters(
        validationResult: ValidationResult,
        manuellOppgaveWithMerknad: ManuellOppgaveKomplett
    ) {
        validationResult.ruleHits.forEach { RULE_HIT_COUNTER.labels(it.ruleName).inc() }
        manuellOppgaveWithMerknad.receivedSykmelding.merknader?.forEach {
            MERKNAD_COUNTER.labels(it.type).inc()
        }
        RULE_HIT_STATUS_COUNTER.labels(validationResult.status.name).inc()
    }

    private suspend fun hentManuellOppgave(
        oppgaveId: Int,
        accessToken: String
    ): ManuellOppgaveKomplett {
        val manuellOppgave = database.hentKomplettManuellOppgave(oppgaveId).firstOrNull()
        if (manuellOppgave == null) {
            logger.error("Fant ikke oppgave med id $oppgaveId")
            throw OppgaveNotFoundException("Fant ikke oppgave med id $oppgaveId")
        }

        if (manuellOppgave.ferdigstilt) {
            logger.warn(
                "oppgaven er allerede ferdigstilt oppgaveId ${manuellOppgave.oppgaveid}, sykmeldingId: ${manuellOppgave.receivedSykmelding.sykmelding.id} merknader: ${manuellOppgave.receivedSykmelding.merknader}"
            )
            throw OppgaveNotFoundException("Fant ikke uløst oppgave med id $oppgaveId")
        }

        val harTilgangTilOppgave =
            istilgangskontrollClient
                .sjekkVeiledersTilgangTilPersonViaAzure(
                    accessToken = accessToken,
                    personFnr = manuellOppgave.receivedSykmelding.personNrPasient,
                )
                .erGodkjent
        if (!harTilgangTilOppgave) {
            throw IkkeTilgangException()
        }
        return manuellOppgave
    }

    suspend fun slettOppgave(sykmeldingId: String) {
        val manuellOppgave = database.hentManuellOppgaveForSykmeldingId(sykmeldingId)

        manuellOppgave?.let {
            if (!it.ferdigstilt) {
                oppgaveService.ferdigstillOppgave(
                    manuellOppgave = it,
                    loggingMeta = LoggingMeta("", null, "", sykmeldingId),
                    enhet = null,
                    veileder = null,
                )
            }
            val antallSlettedeOppgaver = database.slettOppgave(it.oppgaveid)
            logger.info("Slettet $antallSlettedeOppgaver oppgaver")
        }
    }

    suspend fun sendApprec(oppgaveId: Int, apprec: Apprec, loggingMeta: LoggingMeta) {
        try {
            kafkaProducers.kafkaApprecProducer.producer
                .send(ProducerRecord(kafkaProducers.kafkaApprecProducer.apprecTopic, apprec))
                .get()
            logger.info(
                "Apprec kvittering sent til kafka topic {} {}",
                kafkaProducers.kafkaApprecProducer.apprecTopic,
                loggingMeta
            )
            toggleApprecSendt(oppgaveId)
        } catch (ex: Exception) {
            logger.error("Failed to send apprec {}", loggingMeta)
            throw ex
        }
    }

    fun lagOppdatertApprec(manuellOppgave: ManuellOppgave): Apprec =
        Apprec(
            ediloggid = manuellOppgave.apprec.ediloggid,
            msgId = manuellOppgave.apprec.msgId,
            msgTypeVerdi = manuellOppgave.apprec.msgTypeVerdi,
            msgTypeBeskrivelse = manuellOppgave.apprec.msgTypeBeskrivelse,
            genDate = manuellOppgave.apprec.genDate,
            msgGenDate = manuellOppgave.apprec.msgGenDate,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = "Sykmeldingen er til manuell vurdering for tilbakedatering",
            senderOrganisasjon = manuellOppgave.apprec.senderOrganisasjon,
            mottakerOrganisasjon = manuellOppgave.apprec.mottakerOrganisasjon,
            validationResult = manuellOppgave.validationResult,
            ebService =
                if (manuellOppgave.apprec.ebService.isNullOrEmpty()) {
                    manuellOppgave.apprec.ebService
                } else {
                    "Sykmelding"
                },
        )

    fun sendReceivedSykmelding(
        receivedSykmelding: ReceivedSykmeldingWithValidation,
        loggingMeta: LoggingMeta,
        isFerdigstiltSykmelding: Boolean = false,
    ) {
        val topic = kafkaProducers.kafkaRecievedSykmeldingProducer.okSykmeldingTopic
        try {

            val producerRecord =
                ProducerRecord(
                    topic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding,
                )

            logger.info(
                "setting $PROCESSING_TARGET_HEADER to $TSM_PROCESSING_TARGET_VALUE for sykmelding ${receivedSykmelding.sykmelding.id}"
            )

            if (isFerdigstiltSykmelding) {
                producerRecord.headers().add("source", "syfosmmanuell-backend".toByteArray())
            }
            producerRecord
                .headers()
                .add(PROCESSING_TARGET_HEADER, TSM_PROCESSING_TARGET_VALUE.toByteArray())

            kafkaProducers.kafkaRecievedSykmeldingProducer.producer.send(producerRecord).get()
            logger.info(
                "Sendt sykmelding {} to topic {} {}",
                receivedSykmelding.sykmelding.id,
                topic,
                loggingMeta
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to send sykmelding {} to topic {} {}",
                receivedSykmelding.sykmelding.id,
                topic,
                loggingMeta
            )
            throw ex
        }
    }

    suspend fun getOppgaver(): List<UlosteOppgave> {
        return database.getUlosteOppgaver()
    }
}
