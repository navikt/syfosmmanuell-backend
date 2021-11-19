package no.nav.syfo.aksessering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Route.sykmeldingsApi(
    manuellOppgaveService: ManuellOppgaveService
) {
    route("/api/v1") {
        get("/sykmelding/{sykmeldingsId}/exists") {
            val sykmeldingsId = call.parameters["oppgaveid"]!!

            log.info("Mottok kall til /api/v1/sykmelding/{$sykmeldingsId}/exists")

            val finnesSykmelding = manuellOppgaveService.finnesSykmelding(sykmeldingsId)

            if (finnesSykmelding) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }

            return@get
        }
    }
}
