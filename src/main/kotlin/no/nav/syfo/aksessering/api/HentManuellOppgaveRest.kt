package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Routing.hentManuellOppgaver(manuellOppgaveService: ManuellOppgaveService) {
    route("/api/v1") {
        get("/hentManuellOppgave") {
            log.info("Recived call to /api/v1/hentManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            if (oppgaveId == null) {
                log.info("Mangler query parameters: oppgaveid")
                call.respond(HttpStatusCode.BadRequest)
            } else if (manuellOppgaveService.hentManuellOppgaver(oppgaveId).isEmpty()) {
                log.info("Fant ingen uløste manuelloppgaver med oppgaveid {}", oppgaveId)
                call.respond(emptyList<ManuellOppgaveDTO>())
            } else {
                call.respond(manuellOppgaveService.hentManuellOppgaver(oppgaveId))
            }
        }

        get("/harManuellOppgave") {
            log.info("Recived call to /api/v1/harManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            if (oppgaveId == null) {
                log.info("Mangler query parameters: oppgaveid")
                call.respond(HttpStatusCode.BadRequest)
            } else if (manuellOppgaveService.hentManuellOppgaver(oppgaveId).isEmpty()) {
                log.info("Fant ingen uløste manuelloppgaver med oppgaveid {}", oppgaveId)
                call.respond(false)
            } else {
                log.info("Fant ikke ferdigstile manuelloppgaver med oppgaveid {}", oppgaveId)
                call.respond(true)
            }
        }
    }
}
