package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.aksessering.ManuellOppgaveService

fun Routing.hentManuellOppgave(manuellOppgaveService: ManuellOppgaveService) {
    route("/api/v1") {
        get("/manuellOppgave") {
            val manuellOppgaveId = call.parameters["manuellopgaveId"]

            when (manuellOppgaveId) {
                null -> call.respond(HttpStatusCode.BadRequest)
                else -> call.respond(manuellOppgaveService.hentManuellOppgave(manuellOppgaveId))
            }
        }
    }
}
