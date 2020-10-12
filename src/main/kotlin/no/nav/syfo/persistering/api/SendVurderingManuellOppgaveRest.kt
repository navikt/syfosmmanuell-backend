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
    TILBAKEDATERT_MANGLER_BEGRUNNELSE(
        "Sykmelding gjelder som hovedregel fra den dagen du oppsøker behandler. Sykmeldingen din er tilbakedatert uten at det er gitt en god nok begrunnelse for dette. Behandleren din må skrive ut en ny sykmelding og begrunne bedre hvorfor den er tilbakedatert. Din behandler har mottatt melding fra NAV om dette.",
        "Sykmelding gjelder som hovedregel fra den dagen pasienten oppsøker behandler. Sykmeldingen er tilbakedatert uten at det kommer tydelig nok fram hvorfor dette var nødvendig. Sykmeldingen er derfor avvist, og det må skrives en ny hvis sykmelding fortsatt er aktuelt. Pasienten har fått beskjed.",
        "TILBAKEDATERT_MANGLER_BEGRUNNELSE"
    ),
    TILBAKEDATERT_IKKE_GODTATT(
        "NAV kan ikke godta sykmeldingen din fordi den starter før dagen du tok kontakt med behandleren. Trenger du fortsatt sykmelding, må behandleren din skrive en ny som gjelder fra den dagen dere var i kontakt. Behandleren din har fått beskjed fra NAV om dette.",
        "NAV kan ikke godta tilbakedateringen. Sykmeldingen er derfor avvist. Hvis sykmelding fortsatt er aktuelt, må det skrives ny sykmelding der f.o.m.-dato er dagen du var i kontakt med pasienten. Pasienten har fått beskjed.",
        "TILBAKEDATERT_IKKE_GODTATT"
    )
}

data class Result(
    val godkjent: Boolean,
    val avvisningstekst: String?
)
