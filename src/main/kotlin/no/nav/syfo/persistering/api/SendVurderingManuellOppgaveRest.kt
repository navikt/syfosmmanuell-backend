package no.nav.syfo.persistering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import no.nav.syfo.auditLogger.AuditLogger
import no.nav.syfo.auditlogg
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
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

enum class MerknadType {
    UGYLDIG_TILBAKEDATERING,
    TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER,
    DELVIS_GODKJENT,
}

enum class ResultStatus {
    GODKJENT,
    GODKJENT_MED_MERKNAD,
}

data class Result(
    val status: ResultStatus,
    val merknad: MerknadType?,
) {
    fun toMerknad(): Merknad? {
        return when (status) {
            ResultStatus.GODKJENT_MED_MERKNAD -> {
                return when (merknad) {
                    MerknadType.UGYLDIG_TILBAKEDATERING -> {
                        Merknad(
                            type = MerknadType.UGYLDIG_TILBAKEDATERING.name,
                            beskrivelse = null,
                        )
                    }
                    MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER -> {
                        Merknad(
                            type = MerknadType.TILBAKEDATERING_KREVER_FLERE_OPPLYSNINGER.name,
                            beskrivelse = null,
                        )
                    }
                    MerknadType.DELVIS_GODKJENT -> {
                        Merknad(
                            type = MerknadType.DELVIS_GODKJENT.name,
                            beskrivelse = null,
                        )
                    }
                    else -> {
                        throw IllegalArgumentException(
                            "Result with status GODKJENT_MED_MERKNAD missing merknad property"
                        )
                    }
                }
            }
            else -> null
        }
    }
}
