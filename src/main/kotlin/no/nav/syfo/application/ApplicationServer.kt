package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import no.nav.syfo.log
import java.util.concurrent.TimeUnit

class ApplicationServer(
    private val applicationServer: ApplicationEngine,
    private val applicationState: ApplicationState
) {

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                log.info("Er i shutdownhooken")
                this.applicationState.ready = false
                this.applicationServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            }
        )
    }

    fun start() {
        log.info("Starter appen")
        applicationState.alive = true
        applicationServer.start(wait = true)
    }
}
