package no.nav.syfo.aksessering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.auditLogger.AuditLogger
import no.nav.syfo.auditlogg
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.logger
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader
import no.nav.syfo.util.logNAVEpostFromTokenWhenNoAccessToSecureLogs

fun Route.hentManuellOppgaver(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService,
) {
    route("/api/v1") {
        get("/manuellOppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            logger.info("Mottok kall til /api/v1/manuellOppgave/$oppgaveId")
            val accessToken = getAccessTokenFromAuthHeader(call.request)

            if (!manuellOppgaveService.finnesOppgave(oppgaveId)) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            when (hasAccess) {
                false -> {
                    auditlogg.info(
                        AuditLogger()
                            .createcCefMessage(
                                fnr = null,
                                accessToken = accessToken,
                                operation = AuditLogger.Operation.READ,
                                requestPath = "/api/v1/manuellOppgave/$oppgaveId",
                                permit = AuditLogger.Permit.DENY,
                            ),
                    )
                    logNAVEpostFromTokenWhenNoAccessToSecureLogs(
                        accessToken,
                        "/api/v1/manuellOppgave/$oppgaveId"
                    )
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        "Du har ikke tilgang til denne oppgaven."
                    )
                }
                true -> {
                    logger.info("Henter ut oppgave med $oppgaveId")
                    val manuellOppgave = manuellOppgaveService.hentManuellOppgaver(oppgaveId)
                    if (manuellOppgave != null) {
                        auditlogg.info(
                            AuditLogger()
                                .createcCefMessage(
                                    fnr = manuellOppgave.personNrPasient,
                                    accessToken = accessToken,
                                    operation = AuditLogger.Operation.READ,
                                    requestPath = "/api/v1/manuellOppgave/$oppgaveId",
                                    permit = AuditLogger.Permit.PERMIT,
                                ),
                        )
                        call.respond(manuellOppgave)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
        get("/oppgaver") { call.respond(manuellOppgaveService.getOppgaver()) }
        get("/oppgave/sykmelding/{sykmeldingId}") {
            val sykmeldingId = call.parameters["sykmeldingId"]
            if (sykmeldingId == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val manuellOppgave = manuellOppgaveService.hentOppgaveMedId(sykmeldingId)
            if (manuellOppgave != null) {
                logger.info(
                    "Fant oppgaveId ${manuellOppgave.oppgaveId} for sykmelding med id $sykmeldingId"
                )
                call.respond(manuellOppgave)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
