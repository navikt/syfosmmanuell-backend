package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Routing.hentManuellOppgaver(manuellOppgaveService: ManuellOppgaveService) {
    route("/api/v1") {
        get("/manuelloppgave") {
            log.info("Recived call to /api/v1/manuelloppgave")
            val manuellOppgaveId = call.request.queryParameters["manuelloppgaveId"]

            if (manuellOppgaveId.isNullOrEmpty()) {
                log.info("Mangler query parameters: manuelloppgaveId")
                call.respond(HttpStatusCode.BadRequest)
            } else if (manuellOppgaveService.hentManuellOppgaver(manuellOppgaveId).isEmpty()) {
                log.info("Fant ingen manuelloppgaver med akutell manuelloppgaveId")
                call.respond(HttpStatusCode.BadRequest)
            } else {
                call.respond(manuellOppgaveService.hentManuellOppgaver(manuellOppgaveId))
            }
        }
    }
}
