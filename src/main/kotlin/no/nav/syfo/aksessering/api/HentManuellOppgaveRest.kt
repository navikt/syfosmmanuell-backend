package no.nav.syfo.aksessering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.logNAVEpostFromTokenWhenNoAccessToSecureLogs

fun Route.hentManuellOppgaver(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        get("/manuellOppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info("Mottok kall til /api/v1/manuellOppgave/$oppgaveId")
            val accessToken = getAccessTokenFromAuthHeader(call.request)

            if (!manuellOppgaveService.finnesOppgave(oppgaveId)) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            when (hasAccess) {
                false -> {
                    logNAVEpostFromTokenWhenNoAccessToSecureLogs(accessToken, "/manuellOppgave/$oppgaveId")
                    call.respond(HttpStatusCode.Unauthorized, "Du har ikke tilgang til denne oppgaven.")
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
