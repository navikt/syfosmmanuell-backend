package no.nav.syfo.util

import java.io.IOException
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.network-helpers")

suspend inline fun <reified T> retry(
    callName: String,
    vararg legalExceptions: KClass<out Throwable> = arrayOf(IOException::class),
    retryIntervals: Array<Long> = arrayOf(500, 1000, 3000, 5000, 10000),
    exceptionCausedByDepth: Int = 3,
    crossinline block: suspend () -> T
): T {
    for (interval in retryIntervals) {
        try {
            return block()
        } catch (e: Throwable) {
            if (!isCausedBy(e, exceptionCausedByDepth, legalExceptions)) {
                throw e
            }
            log.warn(
                "Failed to execute {}, retrying in $interval ms",
                StructuredArguments.keyValue("callName", callName),
                e
            )
        }
        delay(interval)
    }
    return block()
}

fun isCausedBy(
    throwable: Throwable,
    depth: Int,
    legalExceptions: Array<out KClass<out Throwable>>
): Boolean {
    var current: Throwable = throwable
    for (i in 0.until(depth)) {
        if (legalExceptions.any { it.isInstance(current) }) {
            return true
        }
        current = current.cause ?: break
    }
    return false
}
