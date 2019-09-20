package no.nav.syfo.application

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.api.hentManuellOppgave
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.log
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.persistering.api.sendVurderingManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import org.apache.kafka.clients.producer.KafkaProducer

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    manuellOppgaveService: ManuellOppgaveService,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    sm2013ApprecTopicName: String,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013AutomaticHandlingTopic: String,
    sm2013InvalidHandlingTopic: String
): ApplicationEngine =
    embeddedServer(Netty, env.applicationPort) {
        routing {
            registerNaisApi(applicationState)
            hentManuellOppgave(manuellOppgaveService)
            sendVurderingManuellOppgave(
                manuellOppgaveService,
                kafkaproducerApprec,
                sm2013ApprecTopicName,
                kafkaproducerreceivedSykmelding,
                sm2013AutomaticHandlingTopic,
                sm2013InvalidHandlingTopic
                )
        }
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
        }
        install(StatusPages) {
            exception<Throwable> { cause ->
                call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")

                log.error("Caught exception", cause)
                throw cause
            }
        }
    }
