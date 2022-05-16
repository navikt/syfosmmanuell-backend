package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.aksessering.api.sykmeldingsApi
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.service.IkkeTilgangException
import no.nav.syfo.service.ManuellOppgaveService
import java.util.concurrent.ExecutionException

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    manuellOppgaveService: ManuellOppgaveService,
    jwkProvider: JwkProvider,
    issuer: String,
    authorizationService: AuthorizationService
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        setupAuth(env, jwkProvider, issuer)
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(StatusPages) {
            exception<NumberFormatException> { call, cause ->
                call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                log.error("Caught exception", cause)
                throw cause
            }
            exception<IkkeTilgangException> { call, cause ->
                call.respond(HttpStatusCode.Forbidden)
                log.error("Caught exception", cause)
                throw cause
            }
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception", cause)
                if (cause is ExecutionException) {
                    log.error("Exception is ExecutionException, restarting..")
                    applicationState.ready = false
                    applicationState.alive = false
                }
                throw cause
            }
        }
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Options)
            allowHeader("Content-Type")
            allowHost(env.syfosmmanuellUrl, schemes = listOf("http", "https"))
            allowCredentials = true
        }
        log.info("Setter opp ruter")
        routing {
            registerNaisApi(applicationState)
            authenticate("jwt") {
                hentManuellOppgaver(
                    manuellOppgaveService,
                    authorizationService
                )
                sendVurderingManuellOppgave(
                    manuellOppgaveService,
                    authorizationService
                )
                sykmeldingsApi(manuellOppgaveService)
            }
        }
        log.info("ferdig med å sette opp ruter")
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
