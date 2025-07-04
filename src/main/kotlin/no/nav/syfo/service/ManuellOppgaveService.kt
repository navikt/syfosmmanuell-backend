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

class ManuellOppgaveService(
    private val database: DatabaseInterface,
    private val istilgangskontrollClient: IstilgangskontrollClient,
    private val kafkaProducers: KafkaProducers,
    private val oppgaveService: OppgaveService,
    private val sourceApp: String,
    private val sourceNamespace: String,
) {
    companion object {
        private const val SOURCE_APP = "source-app"
        private const val SOURCE_NAMESPACE = "source-namespace"
    }

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
        val metadata =
            mapOf(
                SOURCE_APP to sourceApp.toByteArray(),
                SOURCE_NAMESPACE to sourceNamespace.toByteArray(),
            )
        sendReceivedSykmelding(
            manuellOppgave.receivedSykmelding.toReceivedSykmeldingWithValidation(validationResult),
            loggingMeta,
            metadata
        )

        if (trengerFlereOpplysninger(manuellOppgave)) {
            oppgaveService.endreOppgave(manuellOppgave, loggingMeta)
            return
        }

        if (manuellOppgave.apprec != null && !erApprecSendt(oppgaveId)) {
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

    fun lagOppdatertApprec(apprec: Apprec, validationResult: ValidationResult): Apprec =
        Apprec(
            ediloggid = apprec.ediloggid,
            msgId = apprec.msgId,
            msgTypeVerdi = apprec.msgTypeVerdi,
            msgTypeBeskrivelse = apprec.msgTypeBeskrivelse,
            genDate = apprec.genDate,
            msgGenDate = apprec.msgGenDate,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = "Sykmeldingen er til manuell vurdering for tilbakedatering",
            senderOrganisasjon = apprec.senderOrganisasjon,
            mottakerOrganisasjon = apprec.mottakerOrganisasjon,
            validationResult = validationResult,
            ebService =
                if (apprec.ebService.isNullOrEmpty()) {
                    apprec.ebService
                } else {
                    "Sykmelding"
                },
        )

    fun sendReceivedSykmelding(
        receivedSykmelding: ReceivedSykmeldingWithValidation,
        loggingMeta: LoggingMeta,
        metadata: Map<String, ByteArray>,
    ) {
        val topic = kafkaProducers.kafkaRecievedSykmeldingProducer.okSykmeldingTopic
        try {

            val producerRecord =
                ProducerRecord(
                    topic,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding,
                )

            metadata.forEach { producerRecord.headers().add(it.key, it.value) }

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
