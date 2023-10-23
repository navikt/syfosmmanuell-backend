package no.nav.syfo.util

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import no.nav.syfo.log

suspend fun retry(times: Int = 3, delay: Duration = 1.seconds, block: suspend () -> Unit) {
    repeat(times) {
        try {
            block()
            return
        } catch (e: Exception) {
            log.warn("Error in retry function", e)
            if (it == times - 1) {
                throw e
            }
            delay(delay)
        }
    }
}
