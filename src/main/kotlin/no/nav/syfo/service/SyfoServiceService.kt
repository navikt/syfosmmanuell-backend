package no.nav.syfo.service

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.kafka.model.KafkaMessageMetadata
import no.nav.syfo.kafka.model.SyfoserviceSykmeldingKafkaMessage
import no.nav.syfo.log
import no.nav.syfo.model.Tilleggsdata
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.extractSyketilfelleStartDato
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

    val syfo = SyfoserviceSykmeldingKafkaMessage(
            helseopplysninger = healthInformation,
            tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, sykmeldingId = sykmeldingId, msgId = msgId, syketilfelleStartDato = syketilfelleStartDato),
            metadata = KafkaMessageMetadata(sykmeldingId, "syfosmmanuell-backend"))

    try {
        syfoserviceProducer.producer.send(ProducerRecord(syfoserviceProducer.topic, sykmeldingId, syfo)).get()
        log.info("Sendt sykmelding til syfoservice-mq-producer {} {}", sykmeldingId, loggingMeta)
    } catch (ex: Exception) {
        log.error("Could not send sykemelding to syfoservice kafka {} {}", sykmeldingId, loggingMeta)
        throw ex
    }
}
