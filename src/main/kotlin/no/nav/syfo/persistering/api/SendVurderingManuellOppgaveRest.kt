package no.nav.syfo.persistering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.auditLogger.AuditLogger
import no.nav.syfo.auditlogg
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.logger
import no.nav.syfo.model.Merknad
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.logNAVEpostFromTokenWhenNoAccessToSecureLogs

fun Route.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService,
) {
    route("/api/v1") {
        post("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            logger.info("Mottok kall til /api/v1/vurderingmanuelloppgave/$oppgaveId")
            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            if (!manuellOppgaveService.finnesOppgave(oppgaveId)) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            if (navEnhet.isNullOrEmpty()) {
                logger.error("Mangler X-Nav-Enhet i http header")
                call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i http header")
                return@post
            }

            when (hasAccess) {
                false -> {
                    auditlogg.info(
                        AuditLogger()
                            .createcCefMessage(
                                fnr = null,
                                accessToken = accessToken,
                                operation = AuditLogger.Operation.WRITE,
                                requestPath = "/api/v1/vurderingmanuelloppgave/$oppgaveId",
                                permit = AuditLogger.Permit.DENY,
                            ),
                    )
                    logNAVEpostFromTokenWhenNoAccessToSecureLogs(
                        accessToken,
                        "/vurderingmanuelloppgave/$oppgaveId"
                    )
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        "Du har ikke tilgang til denne oppgaven."
                    )
                }
                true -> {
                    val result = call.receive<Result>()
                    val merknad = result.toMerknad()

                    val veileder = authorizationService.getVeileder(oppgaveId, accessToken)

                    manuellOppgaveService.ferdigstillManuellBehandling(
                        oppgaveId = oppgaveId,
                        enhet = navEnhet,
                        veileder = veileder,
                        accessToken = accessToken,
                        merknader = if (merknad != null) listOf(merknad) else null,
                    )

                    auditlogg.info(
                        AuditLogger()
                            .createcCefMessage(
                                fnr = null,
                                accessToken = accessToken,
                                operation = AuditLogger.Operation.WRITE,
                                requestPath = "/api/v1/vurderingmanuelloppgave/$oppgaveId",
                                permit = AuditLogger.Permit.PERMIT,
                            ),
                    )

                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

enum class ResultStatus {
    GODKJENT,
    UGYLDIG_TILBAKEDATERING,
    TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER,
    DELVIS_GODKJENT,
}

data class Result(
    val status: ResultStatus,
    val merknad: Merknad? = null,
) {
    fun toMerknad(): Merknad? {
        return when (status) {
            ResultStatus.UGYLDIG_TILBAKEDATERING -> {
                Merknad(
                    type = ResultStatus.UGYLDIG_TILBAKEDATERING.name,
                    beskrivelse = null,
                )
            }
            ResultStatus.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER -> {
                Merknad(
                    type = ResultStatus.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER.name,
                    beskrivelse = null,
                )
            }
            ResultStatus.DELVIS_GODKJENT -> {
                Merknad(
                    type = ResultStatus.DELVIS_GODKJENT.name,
                    beskrivelse = null,
                )
            }
            else -> {
                null
            }
        }
    }
}
