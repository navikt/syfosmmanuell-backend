package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentManuellOppgaver(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        get("/hentManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt() // TODO: change to path param
            // TODO: remove when changing to path param
            if (oppgaveId == null) {
                log.info("Mangler query parameters: oppgaveid")
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            when (hasAccess) {
                false -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                true -> {
                    log.info("Henter ut oppgave med $oppgaveId")
                    call.respond(manuellOppgaveService.hentManuellOppgaver(oppgaveId))
                }
            }
        }
    }
}
