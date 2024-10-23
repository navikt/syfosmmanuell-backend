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

class SelfTest :
    FunSpec({
        context("Successfull liveness and readyness tests") {
            testApplication {
                application {
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    routing { registerNaisApi(applicationState) }
                }

                test("Returns ok on is_alive") {
                    val response = client.get("/internal/is_alive")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("I'm alive! :)", response.bodyAsText())
                }
                test("Returns ok in is_ready") {
                    val response = client.get("/internal/is_ready")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("I'm ready! :)", response.bodyAsText())
                }
            }
        }
        context("Unsuccessful liveness and readyness") {
            testApplication {
                application {
                    val applicationState = ApplicationState()
                    applicationState.ready = false
                    applicationState.alive = false
                    routing { registerNaisApi(applicationState) }
                }

                test("Returns internal server error when liveness check fails") {
                    val response = client.get("/internal/is_alive")
                    assertEquals(HttpStatusCode.InternalServerError, response.status)
                    assertEquals("I'm dead x_x", response.bodyAsText())
                }

                test("Returns internal server error when readyness check fails") {
                    val response = client.get("/internal/is_ready")

                    assertEquals(HttpStatusCode.InternalServerError, response.status)
                    assertEquals("Please wait! I'm not ready :(", response.bodyAsText())
                }
            }
        }
    })
