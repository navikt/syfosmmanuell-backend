package no.nav.syfo.metrics

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.util.pipeline.PipelineContext

val REGEX_OPPGAVEID = """[0-9]{9}""".toRegex()

val REGEX_SYKMELDINGID = """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""".toRegex()

fun monitorHttpRequests(): suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit {
    return {
        val path = context.request.path()
        val label = getLabel(path)
        val timer = HTTP_HISTOGRAM.labels(label).startTimer()
        proceed()
        timer.observeDuration()
    }
}

fun getLabel(path: String): String {
    val utenSykmeldingId = REGEX_SYKMELDINGID.replace(path, ":sykmeldingId")
    return REGEX_OPPGAVEID.replace(utenSykmeldingId, ":oppgaveId")
}
