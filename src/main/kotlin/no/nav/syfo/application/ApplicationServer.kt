package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine
import javax.jms.Connection

class ApplicationServer(
    private val applicationServer: ApplicationEngine,
    private val connection: Connection
) {
    private val applicationState = ApplicationState()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            this.applicationServer.stop(10000, 10000)
            this.connection.close()
        })
    }

    fun start() {
        applicationServer.start(false)
        applicationState.alive = true
    }
}
