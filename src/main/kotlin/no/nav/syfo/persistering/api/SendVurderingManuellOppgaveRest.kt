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

            if (!manuellOppgaveService.finnesOppgave(oppgaveId)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            if (navEnhet.isNullOrEmpty()) {
                log.error("Mangler X-Nav-Enhet i http header")
                call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i http header")
                return@post
            }

            when (hasAccess) {
                false -> {
                    call.respond(HttpStatusCode.Unauthorized, "Du har ikke tilgang til denne oppgaven.")
                }
                true -> {
                    val result = call.receive<Result>()

                    val validationResult = result.toValidationResult()

                    val merknad = result.toMerknad()

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

enum class MerknadType {
    UGYLDIG_TILBAKEDATERING,
    TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER
}

enum class AvvisningType {
    MANGLER_BEGRUNNELSE,
    UGYLDIG_BEGRUNNELSE
}

enum class ResultStatus {
    GODKJENT,
    GODKJENT_MED_MERKNAD
}

data class Result(
    val status: ResultStatus,
    val merknad: MerknadType?
) {
    fun toMerknad(): Merknad? {
        return when (status) {
            ResultStatus.GODKJENT_MED_MERKNAD -> {
                return when (merknad) {
                    MerknadType.UGYLDIG_TILBAKEDATERING -> {
                        Merknad(
                            type = MerknadType.UGYLDIG_TILBAKEDATERING.name,
                            beskrivelse = null
                        )
                    }
                    MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER -> {
                        Merknad(
                            type = MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER.name,
                            beskrivelse = null
                        )
                    }
                    else -> {
                        throw IllegalArgumentException("Result with status GODKJENT_MED_MERKNAD missing merknad property")
                    }
                }
            }
            else -> null
        }
    }

    fun toValidationResult(): ValidationResult {
        return when (status) {
            ResultStatus.GODKJENT -> ValidationResult(Status.OK, emptyList())
            ResultStatus.GODKJENT_MED_MERKNAD -> ValidationResult(Status.OK, emptyList())
        }
    }
}
