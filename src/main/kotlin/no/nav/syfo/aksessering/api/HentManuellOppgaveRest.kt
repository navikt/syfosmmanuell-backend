package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentManuellOppgaver(
    manuellOppgaveService: ManuellOppgaveService,
    syfoTilgangsKontrollClient: SyfoTilgangsKontrollClient
) {
    route("/api/v1") {
        get("/hentManuellOppgave") {
            log.info("Mottok kall til /api/v1/hentManuellOppgave")
            val oppgaveId = call.request.queryParameters["oppgaveid"]?.toInt()
            val accessToken = getAccessTokenFromAuthHeader(call.request)

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
                    log.info("Fant ingen uløste manuelloppgaver med oppgaveid {}",
                        StructuredArguments.keyValue("oppgaveId", oppgaveId))
                    call.respond(HttpStatusCode.NoContent, "Fant ingen uløste manuelle oppgaver med oppgaveid $oppgaveId")
                }
                else -> {
                    log.info("Henter ut oppgave med {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId))

                    val manuellOppgaveDTOList = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    val pasientFnr = manuellOppgaveDTOList.first().receivedSykmelding.personNrPasient

                    val harTilgangTilOppgave = syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(accessToken, pasientFnr)?.harTilgang
                    if (harTilgangTilOppgave != null && harTilgangTilOppgave) {
                        log.info("Veileder har tilgang til {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId))
                        call.respond(manuellOppgaveDTOList)
                    } else {
                        log.warn("Veileder har ikkje tilgang til {}",
                            StructuredArguments.keyValue("oppgaveId", oppgaveId))
                        call.respond(HttpStatusCode.Unauthorized)
                    }
                }
            }
        }
    }
}
