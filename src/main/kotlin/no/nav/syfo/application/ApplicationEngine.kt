package no.nav.syfo.application

import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.ManuellOppgaveService
import no.nav.syfo.aksessering.api.hentManuellOppgave
import no.nav.syfo.application.api.registerNaisApi

fun createApplicationEngine(env: Environment, applicationState: ApplicationState, manuellOppgaveService: ManuellOppgaveService): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        routing {
            registerNaisApi(applicationState)
            hentManuellOppgave(manuellOppgaveService)
        }
    }
