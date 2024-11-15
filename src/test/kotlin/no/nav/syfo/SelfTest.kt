package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.api.registerNaisApi
import org.junit.jupiter.api.Assertions.assertEquals

class SelftestSpek :
    FunSpec(
        {
            context("Successfull liveness and readyness tests") {
                test("Returns ok on is_alive") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = true
                            applicationState.alive = true
                            routing { registerNaisApi(applicationState) }
                        }

                        val response = client.get("/internal/is_alive")

                        assertEquals(response.status, HttpStatusCode.OK)
                        assertEquals(response.bodyAsText(), "I'm alive! :)")
                    }
                }
                test("Returns ok in is_ready") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = true
                            applicationState.alive = true
                            routing { registerNaisApi(applicationState) }
                        }

                        val response = client.get("/internal/is_ready")
                        assertEquals(response.status, HttpStatusCode.OK)
                        assertEquals(response.bodyAsText(), "I'm ready! :)")
                    }
                }
            }
            context("Unsuccessful liveness and readyness") {
                test("Returns internal server error when liveness check fails") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = false
                            applicationState.alive = false
                            routing { registerNaisApi(applicationState) }
                        }
                        val response = client.get("/internal/is_alive")

                        assertEquals(response.status, HttpStatusCode.InternalServerError)
                        assertEquals(response.bodyAsText(), "I'm dead x_x")
                    }
                }

                test("Returns internal server error when readyness check fails") {
                    testApplication {
                        application {
                            val applicationState = ApplicationState()
                            applicationState.ready = false
                            applicationState.alive = false
                            routing { registerNaisApi(applicationState) }
                        }
                        val response = client.get("/internal/is_ready")

                        assertEquals(response.status, HttpStatusCode.InternalServerError)
                        assertEquals(response.bodyAsText(), "Please wait! I'm not ready :(")
                    }
                }
            }
        },
    )
