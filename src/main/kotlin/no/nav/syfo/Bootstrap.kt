package no.nav.syfo

import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.api.registerNaisApi
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sminfotrygd")

fun main() {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })

    log.info("Hello world")
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
            readynessCheck = {
                applicationState.initialized
            },
            livenessCheck = {
                applicationState.running
            }
        )
    }
}