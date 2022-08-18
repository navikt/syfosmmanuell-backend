package no.nav.syfo.api

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import org.amshove.kluent.shouldBeEqualTo

class CORSTest : FunSpec({

    context("CORS-test, anyhost med allow credentials = true") {
        with(TestApplicationEngine()) {
            start()
            application.install(CORS) {
                allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("https"))
                allowCredentials = true
            }
            val applicationState = ApplicationState()
            applicationState.ready = true
            applicationState.alive = true
            application.routing { registerNaisApi(applicationState) }

            test("No origin header") {
                with(handleRequest(HttpMethod.Get, "/internal/is_alive")) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo null
                    response.content shouldBeEqualTo "I'm alive! :)"
                }
            }
            test("Wrong origin header") {
                with(
                    handleRequest(HttpMethod.Get, "/internal/is_ready") {
                        addHeader(HttpHeaders.Origin, "invalid-host")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo null
                    response.content shouldBeEqualTo "I'm ready! :)"
                }
            }
            test("Wrong origin header is empty") {
                with(
                    handleRequest(HttpMethod.Get, "/internal/is_ready") {
                        addHeader(HttpHeaders.Origin, "")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo null
                    response.content shouldBeEqualTo "I'm ready! :)"
                }
            }
            test("Simple credentials") {
                with(
                    handleRequest(HttpMethod.Options, "/internal/is_ready") {
                        addHeader(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                        addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo "https://syfosmmanuell.nais.preprod.local"
                    response.headers[HttpHeaders.Vary] shouldBeEqualTo HttpHeaders.Origin
                    response.headers[HttpHeaders.AccessControlAllowCredentials] shouldBeEqualTo "true"
                }
            }
        }
    }

    context("CORS-test, andre typer") {
        test("Simple Request") {
            with(TestApplicationEngine()) {
                start()
                application.install(CORS) {
                    allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("http", "https"))
                }
                val applicationState = ApplicationState()
                applicationState.ready = true
                applicationState.alive = true
                application.routing { registerNaisApi(applicationState) }

                with(
                    handleRequest(HttpMethod.Get, "/internal/is_ready") {
                        addHeader(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo "https://syfosmmanuell.nais.preprod.local"
                    response.content shouldBeEqualTo "I'm ready! :)"
                }
            }
        }
        test("Simple Null") {
            with(TestApplicationEngine()) {
                start()
                application.install(CORS) {
                    anyHost()
                }
                val applicationState = ApplicationState()
                applicationState.ready = true
                applicationState.alive = true
                application.routing { registerNaisApi(applicationState) }

                with(
                    handleRequest(HttpMethod.Get, "/internal/is_ready") {
                        addHeader(HttpHeaders.Origin, "null")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo "*"
                    response.content shouldBeEqualTo "I'm ready! :)"
                }
            }
        }
        test("Pre flight custom host") {
            with(TestApplicationEngine()) {
                start()
                application.install(CORS) {
                    allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("http", "https"))
                    allowNonSimpleContentTypes = true
                }
                val applicationState = ApplicationState()
                applicationState.ready = true
                applicationState.alive = true
                application.routing { registerNaisApi(applicationState) }

                with(
                    handleRequest(HttpMethod.Options, "/internal/is_ready") {
                        addHeader(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                        addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    response.headers[HttpHeaders.AccessControlAllowOrigin] shouldBeEqualTo "https://syfosmmanuell.nais.preprod.local"
                    response.headers[HttpHeaders.AccessControlAllowHeaders] shouldBeEqualTo "Content-Type"
                    response.headers[HttpHeaders.Vary] shouldBeEqualTo HttpHeaders.Origin
                }
            }
        }
    }
})
