package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.request.RequestCookies
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.service.ManuellOppgaveService

fun Route.hentManuellOppgaver(manuellOppgaveService: ManuellOppgaveService) {
    route("/api/v1") {
        get("/hentManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            val cookies: RequestCookies = call.request.cookies

            log.info("cookies" + objectMapper.writeValueAsString(cookies))

            val principal: JWTPrincipal = call.authentication.principal()!!
            val subject = principal.payload.subject

            log.info("subject: + $subject")

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

        get("/harManuellOppgave") {
            log.info("Mottok kall til /api/v1/harManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()

            when {
                oppgaveId == null -> {
                    log.info("Mangler query parameters: oppgaveid")
                    call.respond(HttpStatusCode.BadRequest)
                }
                manuellOppgaveService.hentManuellOppgaver(oppgaveId).isEmpty() -> {
                    log.info("Fant ingen uløste manuelloppgaver med oppgaveid {}", oppgaveId)
                    call.respond(false)
                }
                else -> {
                    log.info("Fant ikke ferdigstile manuelloppgaver med oppgaveid {}", oppgaveId)
                    call.respond(true)
                }
            }
        }
    }
}
