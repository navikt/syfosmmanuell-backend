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
        test("Skal matche på sykmeldingId også hvis uuid inneholder ni tall") {
            val sykmeldingId = "bdbeebe8-8348-4ac7-a6d7-29c767527080"
            val path = "/sykmelding/$sykmeldingId"

            val oppdatertPath = getLabel(path)

            assertEquals("/sykmelding/:sykmeldingId", oppdatertPath)
        }
    }
})
