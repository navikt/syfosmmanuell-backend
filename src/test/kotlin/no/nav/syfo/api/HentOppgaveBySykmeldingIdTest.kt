package no.nav.syfo.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.clearMocks
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.IstilgangskontrollClient
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ManuellOppgaveMedId
import no.nav.syfo.model.ManuellOppgaveStatus
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.junit.jupiter.api.Assertions.assertEquals

class HentOppgaveBySykmeldingIdTest :
    FunSpec({
        val database = TestDB.database
        val istilgangskontrollClient = mockk<IstilgangskontrollClient>()
        val msGraphClient = mockk<MSGraphClient>()
        val authorizationService =
            AuthorizationService(istilgangskontrollClient, msGraphClient, database)
        val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
        val oppgaveService = mockk<OppgaveService>(relaxed = true)
        val manuellOppgaveService =
            ManuellOppgaveService(
                database,
                istilgangskontrollClient,
                kafkaProducers,
                oppgaveService,
                "app",
                "namespace"
            )

        val sykmeldingId = "test-sykmelding-123"
        val oppgaveid = 308076319
        val manuellOppgave =
            ManuellOppgave(
                receivedSykmelding =
                    receivedSykmelding("navLogId", generateSykmelding(id = sykmeldingId)),
                validationResult =
                    ValidationResult(Status.OK, emptyList(), OffsetDateTime.now(ZoneOffset.UTC)),
                apprec =
                    objectMapper.readValue(
                        Apprec::class
                            .java
                            .getResourceAsStream("/apprecOK.json")!!
                            .readBytes()
                            .toString(
                                Charsets.UTF_8,
                            ),
                    ),
            )

        beforeTest {
            clearMocks(istilgangskontrollClient, msGraphClient, kafkaProducers, oppgaveService)
        }

        afterTest { database.connection.dropData() }

        context("Test av henting av oppgave basert på sykmeldingId") {
            test("Skal hente oppgaveId basert på sykmeldingId") {
                testApplication {
                    application {
                        routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            }
                        }
                    }
                    database.opprettManuellOppgave(
                        manuellOppgave,
                        manuellOppgave.apprec,
                        oppgaveid,
                        ManuellOppgaveStatus.APEN,
                        LocalDateTime.now(),
                    )

                    val response = client.get("/api/v1/oppgave/sykmelding/$sykmeldingId")

                    assertEquals(HttpStatusCode.OK, response.status)
                    val result = objectMapper.readValue<ManuellOppgaveMedId>(response.bodyAsText())
                    assertEquals(oppgaveid, result.oppgaveId)
                    assertEquals(sykmeldingId, result.sykmeldingId)
                }
            }

            test("Skal returnere 404 når sykmeldingId ikke finnes") {
                testApplication {
                    application {
                        routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            }
                        }
                    }

                    val response = client.get("/api/v1/oppgave/sykmelding/ikke-eksisterende-id")

                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
            }

            test("Skal håndtere null sykmeldingId parameter") {
                testApplication {
                    application {
                        routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            }
                        }
                    }

                    val response = client.get("/api/v1/oppgave/sykmelding/")

                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
            }

            test("Skal kunne hente oppgave for ferdigstilt oppgave") {
                testApplication {
                    application {
                        routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            }
                        }
                    }
                    database.opprettManuellOppgave(
                        manuellOppgave,
                        manuellOppgave.apprec,
                        oppgaveid,
                        ManuellOppgaveStatus.FERDIGSTILT,
                        LocalDateTime.now(),
                    )

                    val response = client.get("/api/v1/oppgave/sykmelding/$sykmeldingId")

                    assertEquals(HttpStatusCode.OK, response.status)
                    val result = objectMapper.readValue<ManuellOppgaveMedId>(response.bodyAsText())
                    assertEquals(oppgaveid, result.oppgaveId)
                }
            }

            test("Skal returnere riktig oppgave når det finnes flere oppgaver i databasen") {
                testApplication {
                    application {
                        routing { hentManuellOppgaver(manuellOppgaveService, authorizationService) }
                        install(ContentNegotiation) {
                            jackson {
                                registerKotlinModule()
                                registerModule(JavaTimeModule())
                                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            }
                        }
                    }
                    // Opprett første oppgave
                    database.opprettManuellOppgave(
                        manuellOppgave,
                        manuellOppgave.apprec,
                        oppgaveid,
                        ManuellOppgaveStatus.APEN,
                        LocalDateTime.now(),
                    )

                    // Opprett andre oppgave med annen sykmeldingId
                    val annenSykmeldingId = "annen-sykmelding-456"
                    val annenOppgaveid = 308076320
                    val annenManuellOppgave =
                        ManuellOppgave(
                            receivedSykmelding =
                                receivedSykmelding(
                                    "navLogId2",
                                    generateSykmelding(id = annenSykmeldingId)
                                ),
                            validationResult =
                                ValidationResult(
                                    Status.OK,
                                    emptyList(),
                                    OffsetDateTime.now(ZoneOffset.UTC)
                                ),
                            apprec = manuellOppgave.apprec,
                        )
                    database.opprettManuellOppgave(
                        annenManuellOppgave,
                        annenManuellOppgave.apprec,
                        annenOppgaveid,
                        ManuellOppgaveStatus.APEN,
                        LocalDateTime.now(),
                    )

                    val response = client.get("/api/v1/oppgave/sykmelding/$sykmeldingId")

                    assertEquals(HttpStatusCode.OK, response.status)
                    val result = objectMapper.readValue<ManuellOppgaveMedId>(response.bodyAsText())
                    assertEquals(oppgaveid, result.oppgaveId)
                    assertEquals(sykmeldingId, result.sykmeldingId)
                }
            }
        }
    })
