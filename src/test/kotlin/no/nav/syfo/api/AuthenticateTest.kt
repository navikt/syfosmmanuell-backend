package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.application.setupAuth
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.logger
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.junit.jupiter.api.Assertions.assertEquals

class AuthenticateTest :
    FunSpec({
        val path = "src/test/resources/jwkset.json"
        val uri = Paths.get(path).toUri().toURL()
        val jwkProvider = JwkProviderBuilder(uri).build()
        val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
        val msGraphClient = mockk<MSGraphClient>()
        val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
        val oppgaveService = mockk<OppgaveService>(relaxed = true)
        val database = TestDB.database
        val authorizationService =
            AuthorizationService(istilgangskontrollClient, msGraphClient, database)
        val manuellOppgaveService =
            ManuellOppgaveService(
                database,
                istilgangskontrollClient,
                kafkaProducers,
                oppgaveService,
                "app",
                "namespace"
            )
        val manuelloppgaveId = "1314"
        val manuellOppgave =
            ManuellOppgave(
                receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
                validationResult =
                    ValidationResult(Status.OK, emptyList(), OffsetDateTime.now(ZoneOffset.UTC)),
                apprec =
                    objectMapper.readValue(
                        Apprec::class
                            .java
                            .getResourceAsStream("/apprecOK.json")
                            .readBytes()
                            .toString(
                                Charsets.UTF_8,
                            ),
                    ),
            )
        val oppgaveid = 308076319

        beforeTest {
            database.connection.dropData()
            clearMocks(istilgangskontrollClient, msGraphClient, kafkaProducers, oppgaveService)
            database.opprettManuellOppgave(
                manuellOppgave,
                manuellOppgave.apprec,
                oppgaveid,
                ManuellOppgaveStatus.APEN,
                LocalDateTime.now()
            )
            coEvery {
                istilgangskontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any())
            } returns Tilgang(true)
        }

        context("Autentiseringstest for api") {
            val config =
                Environment(
                    syfosmmanuellUrl = "https://syfosmmanuell",
                    istilgangskontrollScope = "scope",
                    oppgavebehandlingUrl = "oppgave",
                    istilgangskontrollClientUrl = "http://istilgangskontroll",
                    msGraphApiScope = "http://ms.graph.fo/",
                    msGraphApiUrl = "http://ms.graph.fo.ton/",
                    azureTokenEndpoint = "http://ms.token/",
                    azureAppClientSecret = "secret",
                    azureAppClientId = "clientId",
                    oppgaveScope = "oppgave",
                    jwkKeysUrl = "keys",
                    jwtIssuer = "https://sts.issuer.net/myid",
                    databasePassword = "asd",
                    databaseUsername = "asda",
                    dbHost = "",
                    dbName = "",
                    dbPort = "",
                    cluster = "dev-gcp",
                    oppgaveHendelseTopic = "oppgavehendlese",
                    sourceApp = "app",
                    sourceNamespace = "namespace",
                )

            test("Aksepterer gyldig JWT med riktig audience") {
                testApplication {
                    application {
                        setupAuth(config, jwkProvider, "https://sts.issuer.net/myid")
                        routing {
                            authenticate("jwt") {
                                hentManuellOppgaver(manuellOppgaveService, authorizationService)
                            }
                        }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            }
                        }
                        install(StatusPages) {
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    cause.message ?: "Unknown error"
                                )
                                logger.error("Caught exception", cause)
                                throw cause
                            }
                        }
                    }
                    val response =
                        client.get("/api/v1/manuellOppgave/$oppgaveid") {
                            headers {
                                append(
                                    HttpHeaders.Authorization,
                                    "Bearer ${
                                    generateJWT(
                                        "2",
                                        "clientId",
                                        Claim("preferred_username", "firstname.lastname@nav.no"),
                                    )
                                }"
                                )
                            }
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        oppgaveid,
                        objectMapper.readValue<ManuellOppgaveDTO>(response.bodyAsText()).oppgaveid
                    )
                }
            }
            test("Gyldig JWT med feil audience gir Unauthorized") {
                testApplication {
                    application {
                        setupAuth(config, jwkProvider, "https://sts.issuer.net/myid")
                        routing {
                            authenticate("jwt") {
                                hentManuellOppgaver(manuellOppgaveService, authorizationService)
                            }
                        }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            }
                        }
                        install(StatusPages) {
                            exception<Throwable> { call, cause ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    cause.message ?: "Unknown error"
                                )
                                logger.error("Caught exception", cause)
                                throw cause
                            }
                        }
                    }
                    val response =
                        client.get("/api/v1/manuellOppgave/$oppgaveid") {
                            headers {
                                append(
                                    HttpHeaders.Authorization,
                                    "Bearer ${
                                    generateJWT(
                                        "2",
                                        "annenClientId",
                                        Claim("preferred_username", "firstname.lastname@nav.no"),
                                    )
                                }"
                                )
                            }
                        }

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    assertEquals("", response.bodyAsText())
                }
            }
        }
    })
