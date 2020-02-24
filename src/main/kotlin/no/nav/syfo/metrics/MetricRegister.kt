package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "syfosmmanuellbackend"

val MESSAGE_STORED_IN_DB_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("message_stored_in_db_count")
        .help("Counts the number of messages stored in db")
        .register()

val OPPRETT_OPPGAVE_COUNTER: Counter = Counter.Builder()
        .namespace(METRICS_NS)
        .name("opprett_oppgave_counter")
        .help("Registers a counter for each oppgave that is created")
        .register()

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
        .labelNames("path")
        .name("requests_duration_seconds")
        .help("http requests durations for incoming requests in seconds")
        .register()

val INCOMING_MESSAGE_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("incoming_message_count")
        .help("Counts the number of incoming messages")
        .register()
