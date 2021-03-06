package no.nav.syfo.application

import com.auth0.jwk.JwkProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import java.lang.NumberFormatException
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
import no.nav.syfo.metrics.monitorHttpRequests
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService

@KtorExperimentalAPI
fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    manuellOppgaveService: ManuellOppgaveService,
    vaultSecrets: VaultSecrets,
    jwkProvider: JwkProvider,
    issuer: String,
    authorizationService: AuthorizationService
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        setupAuth(vaultSecrets, jwkProvider, issuer)
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        install(StatusPages) {
            exception<NumberFormatException> { cause ->
                call.respond(HttpStatusCode.BadRequest, "oppgaveid is not a number")
                log.error("Caught exception", cause)
                throw cause
            }
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                log.error("Caught exception", cause)
                throw cause
            }
        }
        install(CORS) {
            method(HttpMethod.Get)
            method(HttpMethod.Post)
            method(HttpMethod.Put)
            method(HttpMethod.Options)
            header("Content-Type")
            host(env.syfosmmanuellUrl, schemes = listOf("http", "https"))
            allowCredentials = true
        }
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
            }
        }
        intercept(ApplicationCallPipeline.Monitoring, monitorHttpRequests())
    }
