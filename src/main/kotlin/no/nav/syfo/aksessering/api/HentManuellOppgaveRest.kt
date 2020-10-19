package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import javax.ws.rs.ForbiddenException
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.persistering.error.OppgaveNotFoundException
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

fun Route.hentManuellOppgaver(
    manuellOppgaveService: ManuellOppgaveService
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
                else -> {
                    log.info("Henter ut oppgave med {}",
                    StructuredArguments.keyValue("oppgaveId", oppgaveId))

                    try {
                        val manuellOppgaveDTOList = manuellOppgaveService.hentManuelleOppgaver(oppgaveId, accessToken)
                        call.respond(manuellOppgaveDTOList)
                    } catch (e: OppgaveNotFoundException) {
                        call.respond(HttpStatusCode.NotFound, e.message)
                    } catch (e: ForbiddenException) {
                        call.respond(HttpStatusCode.Unauthorized)
                    } catch (e: Exception) {
                        log.info("Unknown error", e)
                        throw e
                    }
                }
            }
        }
    }
}
