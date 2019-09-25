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
            val pasientFnr = call.request.queryParameters["fnr"]

            if (pasientFnr.isNullOrEmpty()) {
                log.info("Mangler query parameters: fnr")
                call.respond(HttpStatusCode.BadRequest)
            } else if (manuellOppgaveService.hentManuellOppgaver(pasientFnr).isEmpty()) {
                log.info("Fant ingen ikke ferdigstile manuelloppgaver for akutell fnr")
                call.respond(emptyList<ManuellOppgaveDTO>())
            } else {
                call.respond(manuellOppgaveService.hentManuellOppgaver(pasientFnr))
            }
        }

        get("/harManuellOppgave") {
            log.info("Recived call to /api/v1/harManuellOppgave")
            val pasientFnr = call.request.queryParameters["fnr"]

            if (pasientFnr.isNullOrEmpty()) {
                log.info("Mangler query parameters: fnr")
                call.respond(HttpStatusCode.BadRequest)
            } else if (manuellOppgaveService.hentManuellOppgaver(pasientFnr).isEmpty()) {
                log.info("Fant ingen ikke ferdigstile manuelloppgaver for akutell fnr")
                call.respond(false)
            } else {
                log.info("Fant ikke ferdigstile manuelloppgaver for akutell fnr")
                call.respond(true)
            }
        }
    }
}
