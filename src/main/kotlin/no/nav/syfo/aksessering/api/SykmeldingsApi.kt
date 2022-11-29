package no.nav.syfo.aksessering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.log
import no.nav.syfo.service.ManuellOppgaveService

fun Route.sykmeldingsApi(
    manuellOppgaveService: ManuellOppgaveService
) {
    route("/api/v1") {
        get("/sykmelding/{sykmeldingsId}") {
            val sykmeldingsId = call.parameters["sykmeldingsId"]!!

            log.info("Mottok kall til /api/v1/sykmelding/$sykmeldingsId")

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
