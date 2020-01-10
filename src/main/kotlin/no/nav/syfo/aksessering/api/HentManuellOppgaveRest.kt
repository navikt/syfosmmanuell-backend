package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Route.hentManuellOppgaver(manuellOppgaveService: ManuellOppgaveService) {
    route("/api/v1") {
        get("/hentManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            when {
                oppgaveId == null -> {
                    log.info("Mangler query parameters: oppgaveid")
                    call.respond(HttpStatusCode.BadRequest)
                }
                manuellOppgaveService.hentManuellOppgaver(oppgaveId).isEmpty() -> {
                    log.info("Fant ingen uløste manuelloppgaver med oppgaveid {}", oppgaveId)
                    call.respond(emptyList<ManuellOppgaveDTO>())
                }
                else -> {
                    log.info("Henter ut oppgave med oppgaveid: {}", oppgaveId)
                    call.respond(manuellOppgaveService.hentManuellOppgaver(oppgaveId))
                }
            }
        }
    }
}
