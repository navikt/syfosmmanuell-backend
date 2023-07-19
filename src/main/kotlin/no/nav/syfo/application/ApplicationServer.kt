package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import no.nav.syfo.elector.LeaderElectorService
import java.util.concurrent.TimeUnit

class ApplicationServer(
    private val applicationServer: ApplicationEngine,
    private val applicationState: ApplicationState,
    private val leaderElectorService: LeaderElectorService,
) {

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                this.applicationState.ready = false
                leaderElectorService.stop()
                this.applicationServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
            },
        )
    }

    fun start() {
        applicationState.alive = true
        applicationServer.start(true)
    }
}
