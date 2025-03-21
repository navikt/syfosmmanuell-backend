package no.nav.syfo.aksessering.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import no.nav.syfo.logger
import no.nav.syfo.service.ManuellOppgaveService

fun Route.sykmeldingsApi(
    manuellOppgaveService: ManuellOppgaveService,
) {
    route("/api/v1") {
        get("/sykmelding/{sykmeldingsId}") {
            val sykmeldingsId = call.parameters["sykmeldingsId"]!!

            try {
                val finnesSykmelding = manuellOppgaveService.finnesSykmelding(sykmeldingsId)

                when (finnesSykmelding) {
                    true -> call.respond(HttpStatusCode.OK)
                    else -> call.respond(HttpStatusCode.NotFound)
                }
            } catch (e: Exception) {
                logger.error("Noe gikk galt ved sjekk av sykmelding med id $sykmeldingsId", e)
                throw e
            }
        }
    }
}
