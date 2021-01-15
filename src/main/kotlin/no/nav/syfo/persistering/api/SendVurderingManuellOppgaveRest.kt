package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        post("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info("Mottok kall til /api/v1/vurderingmanuelloppgave/$oppgaveId")
            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            if (navEnhet.isNullOrEmpty()) {
                log.error("Mangler X-Nav-Enhet i http header")
                call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i http header")
                return@post
            }

            when (hasAccess) {
                false -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                true -> {
                    val result: Result = call.receive()

                    val validationResult = result.tilValidationResult()

                    val merknad = result.tilMerknad()

                    val veileder = authorizationService.getVeileder(accessToken)

                    manuellOppgaveService.ferdigstillManuellBehandling(
                        oppgaveId = oppgaveId,
                        enhet = navEnhet,
                        veileder = veileder,
                        validationResult = validationResult,
                        accessToken = accessToken,
                        merknader = if (merknad != null) listOf(merknad) else null
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

fun Result.tilValidationResult(): ValidationResult {
    return when (status) {
        ResultStatus.GODKJENT -> ValidationResult(Status.OK, emptyList())
        ResultStatus.UGYLDIG_TILBAKEDATERING -> ValidationResult(Status.OK, emptyList())
        ResultStatus.UGYLDIG_BEGRUNNELSE -> {
            val regel = RuleInfoTekst.TILBAKEDATERT_MANGLER_BEGRUNNELSE
            ValidationResult(
                Status.INVALID,
                listOf(
                    RuleInfo(regel.name, regel.messageForSender, regel.messageForUser, Status.INVALID)
                )
            )
        }
    }
}

fun Result.tilMerknad(): Merknad? {
    return when (status) {
        ResultStatus.UGYLDIG_TILBAKEDATERING -> {
            Merknad(type = MerknadType.UGYLDIG_TILBAKEDATERING.name, beskrivelse = "TODO")
        }
        else -> null
    }
}

enum class RuleInfoTekst(val messageForUser: String, val messageForSender: String, val rulename: String) {
    TILBAKEDATERT_MANGLER_BEGRUNNELSE(
        "Sykmelding gjelder som hovedregel fra den dagen du oppsøker behandler. Sykmeldingen din er tilbakedatert uten at det er gitt en god nok begrunnelse for dette. Behandleren din må skrive ut en ny sykmelding og begrunne bedre hvorfor den er tilbakedatert. Din behandler har mottatt melding fra NAV om dette.",
        "Sykmelding gjelder som hovedregel fra den dagen pasienten oppsøker behandler. Sykmeldingen er tilbakedatert uten at det kommer tydelig nok fram hvorfor dette var nødvendig. Sykmeldingen er derfor avvist, og det må skrives en ny hvis det fortsatt er aktuelt med sykmelding. Pasienten har fått beskjed om å vente på ny sykmelding fra deg.",
        "TILBAKEDATERT_MANGLER_BEGRUNNELSE"
    )
}

enum class MerknadType {
    UGYLDIG_TILBAKEDATERING
}

enum class ResultStatus {
    GODKJENT,
    UGYLDIG_TILBAKEDATERING,
    UGYLDIG_BEGRUNNELSE
}

data class Result(
    val status: ResultStatus
)
