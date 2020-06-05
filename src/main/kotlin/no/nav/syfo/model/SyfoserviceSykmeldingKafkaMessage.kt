package no.nav.syfo.kafka.model

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.Tilleggsdata

data class SyfoserviceSykmeldingKafkaMessage(
    val metadata: KafkaMessageMetadata,
    val helseopplysninger: HelseOpplysningerArbeidsuforhet,
    val tilleggsdata: Tilleggsdata
)
