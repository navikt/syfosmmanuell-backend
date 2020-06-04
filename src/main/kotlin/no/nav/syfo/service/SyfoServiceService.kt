package no.nav.syfo.service

import java.io.ByteArrayOutputStream
import java.util.Base64
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import no.nav.syfo.model.Syfo
import no.nav.syfo.model.Tilleggsdata
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractSyketilfelleStartDato
import no.nav.syfo.util.sykmeldingMarshaller
import org.apache.kafka.clients.producer.ProducerRecord

fun notifySyfoService(
    syfoserviceProducer: KafkaProducers.KafkaSyfoserviceProducer,
    ediLoggId: String,
    sykmeldingId: String,
    msgId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    loggingMeta: LoggingMeta
) {

    val syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation)
    val sykmelding = convertSykemeldingToBase64(healthInformation)
    val syfo = Syfo(
            tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, sykmeldingId = sykmeldingId, msgId = msgId, syketilfelleStartDato = syketilfelleStartDato),
            sykmelding = Base64.getEncoder().encodeToString(sykmelding))

    try {
        syfoserviceProducer.producer.send(ProducerRecord(syfoserviceProducer.topic, sykmeldingId, syfo)).get()
        log.info("Sendt sykmelding til syfoservice-mq-producer {} {}", sykmeldingId, loggingMeta)
    } catch (ex: Exception) {
        log.error("Could not send sykemelding to syfoservice kafka {} {}", sykmeldingId, loggingMeta)
        throw ex
    }
}

fun convertSykemeldingToBase64(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): ByteArray =
        ByteArrayOutputStream().use {
            sykmeldingMarshaller.marshal(helseOpplysningerArbeidsuforhet, it)
            it
        }.toByteArray()
