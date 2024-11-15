package no.nav.syfo.api

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import org.junit.jupiter.api.Assertions.assertEquals

class CORSTest :
    FunSpec({
        context("CORS-test, anyhost med allow credentials = true") {
            test("No origin header") {
                testApplication {
                    application {
                        install(CORS) {
                            allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("https"))
                            allowCredentials = true
                        }
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { registerNaisApi(applicationState) }
                    }
                    val response = client.get("/internal/is_alive")

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
                    assertEquals("I'm alive! :)", response.bodyAsText())
                }
            }
            test("Wrong origin header") {
                testApplication {
                    application {
                        install(CORS) {
                            allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("https"))
                            allowCredentials = true
                        }
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { registerNaisApi(applicationState) }
                    }
                    val response =
                        client.get("/internal/is_ready") {
                            headers { append(HttpHeaders.Origin, "invalid-host") }
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
                    assertEquals("I'm ready! :)", response.bodyAsText())
                }
            }
            test("Wrong origin header is empty") {
                testApplication {
                    application {
                        install(CORS) {
                            allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("https"))
                            allowCredentials = true
                        }
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { registerNaisApi(applicationState) }
                    }
                    val response =
                        client.get("/internal/is_ready") {
                            headers { append(HttpHeaders.Origin, "") }
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(null, response.headers[HttpHeaders.AccessControlAllowOrigin])
                    assertEquals("I'm ready! :)", response.bodyAsText())
                }
            }
            test("Simple credentials") {
                testApplication {
                    application {
                        install(CORS) {
                            allowHost("syfosmmanuell.nais.preprod.local", schemes = listOf("https"))
                            allowCredentials = true
                        }
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { registerNaisApi(applicationState) }
                    }
                    val response =
                        client.get("/internal/is_ready") {
                            headers {
                                append(
                                    HttpHeaders.Origin,
                                    "https://syfosmmanuell.nais.preprod.local"
                                )
                                append(HttpHeaders.AccessControlRequestMethod, "GET")
                            }
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        "https://syfosmmanuell.nais.preprod.local",
                        response.headers[HttpHeaders.AccessControlAllowOrigin]
                    )
                    assertEquals(HttpHeaders.Origin, response.headers[HttpHeaders.Vary])
                    assertEquals(
                        "true",
                        response.headers[HttpHeaders.AccessControlAllowCredentials]
                    )
                }
            }
        }

        context("CORS-test, andre typer") {
            test("Simple Request") {
                testApplication {
                    application {
                        install(CORS) {
                            allowHost(
                                "syfosmmanuell.nais.preprod.local",
                                schemes = listOf("http", "https")
                            )
                        }
                        val applicationState = ApplicationState()
                        applicationState.ready = true
                        applicationState.alive = true
                        routing { registerNaisApi(applicationState) }
                    }

                    val response =
                        client.get("/internal/is_ready") {
                            headers {
                                append(
                                    HttpHeaders.Origin,
                                    "https://syfosmmanuell.nais.preprod.local"
                                )
                            }
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        "https://syfosmmanuell.nais.preprod.local",
                        response.headers[HttpHeaders.AccessControlAllowOrigin]
                    )
                    assertEquals("I'm ready! :)", response.bodyAsText())
                }
            }
        }
        test("Simple Null") {
            testApplication {
                application {
                    install(CORS) { anyHost() }
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    routing { registerNaisApi(applicationState) }
                }

                val response =
                    client.get("/internal/is_ready") {
                        headers { append(HttpHeaders.Origin, "null") }
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("I'm ready! :)", response.bodyAsText())
            }
        }
        test("Pre flight custom host") {
            testApplication {
                application {
                    install(CORS) {
                        allowHost(
                            "syfosmmanuell.nais.preprod.local",
                            schemes = listOf("http", "https")
                        )
                        allowNonSimpleContentTypes = true
                    }
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    routing { registerNaisApi(applicationState) }
                }

                val response =
                    client.get("/internal/is_ready") {
                        header(HttpHeaders.Origin, "https://syfosmmanuell.nais.preprod.local")
                        header(HttpHeaders.AccessControlRequestMethod, "GET")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(
                    "https://syfosmmanuell.nais.preprod.local",
                    response.headers[HttpHeaders.AccessControlAllowOrigin]
                )
                assertEquals(HttpHeaders.Origin, response.headers[HttpHeaders.Vary])
            }
        }
    })
