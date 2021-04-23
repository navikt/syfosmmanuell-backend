package no.nav.syfo.brukernotificasjon.kafka

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
import org.apache.kafka.clients.producer.ProducerRecord

class BeskjedProducer(private val brukernotifikasjonProducer: KafkaProducers.BrukernotifikasjonProducer) {

    fun sendBeskjed(nokkel: Nokkel, beskjed: Beskjed) {
        brukernotifikasjonProducer.producer.send(ProducerRecord(brukernotifikasjonProducer.topic, nokkel, beskjed)).get()
        log.info("Sendt brukernotifikasjon for sykmelding som er til manuell behandling")
    }
}
