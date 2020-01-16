package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.auth.parseAuthorizationHeader
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Route.hentManuellOppgaver(
    manuellOppgaveService: ManuellOppgaveService,
    syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    route("/api/v1") {
        get("/hentManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()
            val authHeader = call.request.parseAuthorizationHeader()
            var accessToken: String? = null
            if (!(authHeader == null ||
                        authHeader !is HttpAuthHeader.Single ||
                        authHeader.authScheme != "Bearer")) {
                accessToken = authHeader.blob
            }

            when {
                accessToken == null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
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
                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    val pasientFnr = manuellOppgaveDTOList.first().receivedSykmelding.personNrPasient

                    val harTilgangTilOppgave = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(accessToken, pasientFnr)?.harTilgang
                    if (harTilgangTilOppgave != null && harTilgangTilOppgave) {
                        call.respond(manuellOppgaveDTOList)
                    } else {
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }
            }
        }
    }
}
