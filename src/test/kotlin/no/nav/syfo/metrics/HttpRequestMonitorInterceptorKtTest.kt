package no.nav.syfo.metrics

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class HttpRequestMonitorInterceptorKtTest : Spek({
    describe("Regex for metrikker") {
        it("Skal matche på aktørid") {
            val path = "/manuellOppgave/334866611"

            val oppdatertPath = REGEX.replace(path, ":oppgaveId")

            oppdatertPath shouldBeEqualTo "/manuellOppgave/:oppgaveId"
        }
    }
})
