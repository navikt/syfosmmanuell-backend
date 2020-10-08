package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService
) {
    route("/api/v1") {
        post("/vurderingmanuelloppgave/{oppgaveid}") {
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
                    val result: Result = call.receive()
                    if (!result.godkjent && result.avvisningstekst.isNullOrEmpty()) {
                        log.warn("Sykmelding for oppgaveid $oppgaveId er avvist, men mangler begrunnelse")
                        call.respond(HttpStatusCode.BadRequest)
                    }
                    val validationResult = result.tilValidationResult()
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

fun Result.tilValidationResult(): ValidationResult {
    return if (godkjent) {
        ValidationResult(Status.OK, emptyList())
    } else {
        val regel = RuleInfoTekst.valueOf(avvisningstekst!!)
        ValidationResult(Status.INVALID, listOf(RuleInfo(ruleName = regel.name, messageForSender = regel.messageForSender, messageForUser = regel.messageForUser, ruleStatus = Status.INVALID)))
    }
}

enum class RuleInfoTekst(val messageForUser: String, val messageForSender: String, val rulename: String) {
    TILBAKEDATERT_FORSTEGANGS("Bruker", "lege", "regel1"),
    TILBAKEDATERT_FORLENGELSE("Bruker", "lege", "regel2")
}

data class Result(
    val godkjent: Boolean,
    val avvisningstekst: String?
)
