package no.nav.syfo.metrics

import io.kotest.core.spec.style.FunSpec
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.UUID

class HttpRequestMonitorInterceptorKtTest : FunSpec({
    context("Regex for metrikker") {
        test("Skal matche på oppgaveId") {
            val path = "/manuellOppgave/334866611"

            val oppdatertPath = getLabel(path)

            assertEquals("/manuellOppgave/:oppgaveId", oppdatertPath)
        }
        test("Skal matche på sykmeldingId") {
            val sykmeldingId = UUID.randomUUID().toString()
            val path = "/sykmelding/$sykmeldingId"

            val oppdatertPath = getLabel(path)

            assertEquals("/sykmelding/:sykmeldingId", oppdatertPath)
        }
    }
})
