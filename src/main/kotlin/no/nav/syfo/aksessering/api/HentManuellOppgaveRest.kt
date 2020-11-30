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
        get("/manuellOppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info("Mottok kall til /api/v1/manuellOppgave/$oppgaveId")
            val accessToken = getAccessTokenFromAuthHeader(call.request)

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            when (hasAccess) {
                false -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                true -> {
                    log.info("Henter ut oppgave med $oppgaveId")
                    val manuellOppgave = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    if (manuellOppgave != null) {
                        call.respond(manuellOppgave)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
