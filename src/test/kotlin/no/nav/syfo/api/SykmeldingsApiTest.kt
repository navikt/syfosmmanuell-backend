package no.nav.syfo.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.mockk
import no.nav.syfo.aksessering.api.sykmeldingsApi
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.model.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.oppgave.service.OppgaveService
import no.nav.syfo.persistering.db.opprettManuellOppgave
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.generateJWT
import no.nav.syfo.testutil.generateSykmelding
import no.nav.syfo.testutil.receivedSykmelding
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.UUID

class SykmeldingsApiTest : FunSpec({

    val database = TestDB.database
    val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)

    val sykmeldingsId = UUID.randomUUID().toString()
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(sykmeldingsId, generateSykmelding(sykmeldingsId)),
        validationResult = ValidationResult(Status.OK, emptyList()),
        apprec = objectMapper.readValue(
            Apprec::class.java.getResourceAsStream("/apprecOK.json")!!.readBytes().toString(
                Charsets.UTF_8,
            ),
        ),
    )
    val oppgaveid = 308076319

    beforeTest {
        clearMocks(syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)
    }

    afterTest {
        database.connection.dropData()
    }

    context("Test av henting av manuelle oppgaver api") {
        with(TestApplicationEngine()) {
            start()
            application.routing { sykmeldingsApi(manuellOppgaveService) }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }

            test("Skal få 200 OK hvis sykmelding finnes") {
                database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmelding/$sykmeldingsId") {
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    },
                ) {
                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }
            test("Skal returnere notFound når det ikkje finnes noen oppgaver med oppgitt id") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/sykmelding/$sykmeldingsId") {
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    },
                ) {
                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            }
        }
    }
})
