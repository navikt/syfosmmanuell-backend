package no.nav.syfo.api

import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CORSTest : Spek({

    describe("CORS-test, anyhost med allow credentials = true") {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                anyHost()
                allowCredentials = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            it("No origin header") {
                with(handleRequest(HttpMethod.Get, "/is_alive")) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual null
                    response.content shouldEqual "I'm alive! :)"
                }
            }
            it("Wrong origin header") {
                with(handleRequest(HttpMethod.Get, "/is_ready") {
                    addHeader(HttpHeaders.Origin, "invalid-host")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual null
                    response.content shouldEqual "I'm ready! :)"
                }
            }
            it("Wrong origin header is empty") {
                with(handleRequest(HttpMethod.Get, "/is_ready") {
                    addHeader(HttpHeaders.Origin, "")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual null
                    response.content shouldEqual "I'm ready! :)"
                }
            }
            it("Simple credentials") {
                with(handleRequest(HttpMethod.Options, "/is_ready") {
                    addHeader(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                    addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual "https://syfosmmanuell.nais.preprod.local"
                    response.headers[HttpHeaders.Vary] shouldEqual HttpHeaders.Origin
                    response.headers[HttpHeaders.AccessControlAllowCredentials] shouldEqual "true"
                }
            }
        }
    }

    describe("CORS-test, andre typer") {
        it("Simple Request") {
            with(TestApplicationEngine()) {
                start()
                application.install(CORS) {
                    host("syfosmmanuell.nais.preprod.local", schemes = listOf("http", "https"))
                }
                val applicationState = ApplicationState()
                applicationState.ready = true
                applicationState.alive = true
                application.routing { registerNaisApi(applicationState) }

                with(handleRequest(HttpMethod.Get, "/is_ready") {
                    addHeader(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual "https://syfosmmanuell.nais.preprod.local"
                    response.content shouldEqual "I'm ready! :)"
                }
            }
        }
        it("Simple Null") {
            with(TestApplicationEngine()) {
                start()
                application.install(CORS) {
                    anyHost()
                }
                val applicationState = ApplicationState()
                applicationState.ready = true
                applicationState.alive = true
                application.routing { registerNaisApi(applicationState) }

                with(handleRequest(HttpMethod.Get, "/is_ready") {
                    addHeader(HttpHeaders.Origin, "null")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual "*"
                    response.content shouldEqual "I'm ready! :)"
                }
            }
        }
        it("Pre flight custom host") {
            with(TestApplicationEngine()) {
                start()
                application.install(CORS) {
                    host("syfosmmanuell.nais.preprod.local", schemes = listOf("http", "https"))
                    allowNonSimpleContentTypes = true
                }
                val applicationState = ApplicationState()
                applicationState.ready = true
                applicationState.alive = true
                application.routing { registerNaisApi(applicationState) }

                with(handleRequest(HttpMethod.Options, "/is_ready") {
                    addHeader(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                    addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                }) {
                    response.status() shouldEqual HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldEqual "https://syfosmmanuell.nais.preprod.local"
                    response.headers[HttpHeaders.AccessControlAllowHeaders] shouldEqual "Content-Type"
                    response.headers[HttpHeaders.Vary] shouldEqual HttpHeaders.Origin
                }
            }
        }
    }
})
