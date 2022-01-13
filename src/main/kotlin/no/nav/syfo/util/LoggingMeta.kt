package no.nav.syfo.util

import no.nav.syfo.oppgave.exceptions.OpprettOppgaveException

data class LoggingMeta(
    val mottakId: String,
    val orgNr: String?,
    val msgId: String,
    val sykmeldingId: String
)

open class TrackableException(override val cause: Throwable, val loggingMeta: LoggingMeta) : RuntimeException()
class TrackableOpprettOppgaveException(cause: Throwable, loggingMeta: LoggingMeta) : TrackableException(cause, loggingMeta)

suspend fun <O> wrapExceptions(loggingMeta: LoggingMeta, block: suspend () -> O): O {
    try {
        return block()
    } catch (e: Exception) {
        when (e) {
            is OpprettOppgaveException -> {
                throw TrackableOpprettOppgaveException(e, loggingMeta)
            } else -> {
                throw TrackableException(e, loggingMeta)
            }
        }
    }
}
