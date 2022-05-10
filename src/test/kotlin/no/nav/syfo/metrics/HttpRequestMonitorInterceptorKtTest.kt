package no.nav.syfo.metrics

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

class HttpRequestMonitorInterceptorKtTest : FunSpec({
    context("Regex for metrikker") {
        test("Skal matche på aktørid") {
            val path = "/manuellOppgave/334866611"

            val oppdatertPath = REGEX.replace(path, ":oppgaveId")

            oppdatertPath shouldBeEqualTo "/manuellOppgave/:oppgaveId"
        }
    }
})
