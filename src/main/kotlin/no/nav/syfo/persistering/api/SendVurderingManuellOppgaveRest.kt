package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.put
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService
) {
    route("/api/v1") {
        put("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info(
                "Mottok eit kall til /api/v1/vurderingmanuelloppgave med {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId)
            )
            when (val accessToken = getAccessTokenFromAuthHeader(call.request)) {
                null -> {
                    log.info("Mangler JWT Bearer token i HTTP header")
                    call.respond(HttpStatusCode.BadRequest)
                }
                else -> {
                    val validationResult: ValidationResult = call.receive()
                    manuellOppgaveService.ferdigstillManuellBehandling(
                        oppgaveId = oppgaveId,
                        validationResult = validationResult,
                        accessToken = accessToken
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
