package no.nav.syfo

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import org.amshove.kluent.shouldEqual
import org.junit.Test

internal class SelfTest {

    @Test
    internal fun `Returns ok on is_alive`() {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(handleRequest(HttpMethod.Get, "/is_alive")) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "I'm alive! :)"
            }
        }
    }

    @Test
    internal fun `Returns ok in is_ready`() {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            with(handleRequest(HttpMethod.Get, "/is_ready")) {
                response.status() shouldEqual HttpStatusCode.OK
                response.content shouldEqual "I'm ready! :)"
            }
        }
    }

    @Test
    internal fun `Returns internal server error when liveness check fails`() {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = false
            applicationState.alive = false
            application.routing { registerNaisApi(applicationState) }

            with(handleRequest(HttpMethod.Get, "/is_alive")) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual "I'm dead x_x"
            }
        }
    }

    @Test
    internal fun `Returns internal server error when readyness check fails`() {
        with(TestApplicationEngine()) {
            start()
            val applicationState = ApplicationState()
            applicationState.ready = false
            applicationState.alive = false
            application.routing { registerNaisApi(applicationState) }
            with(handleRequest(HttpMethod.Get, "/is_ready")) {
                response.status() shouldEqual HttpStatusCode.InternalServerError
                response.content shouldEqual "Please wait! I'm not ready :("
            }
        }
    }
}
