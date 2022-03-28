package no.nav.syfo.api

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.aksessering.ManuellOppgaveDTO
import no.nav.syfo.aksessering.api.hentManuellOppgaver
import no.nav.syfo.application.setupAuth
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.clients.KafkaProducers
import no.nav.syfo.log
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
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object AuthenticateTest : Spek({
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    val jwkProvider = JwkProviderBuilder(uri).build()
    val syfoTilgangsKontrollClient = mockk<SyfoTilgangsKontrollClient>()
    val msGraphClient = mockk<MSGraphClient>()
    val kafkaProducers = mockk<KafkaProducers>(relaxed = true)
    val oppgaveService = mockk<OppgaveService>(relaxed = true)

    val database = TestDB.database
    val authorizationService = AuthorizationService(syfoTilgangsKontrollClient, msGraphClient, database)
    val manuellOppgaveService = ManuellOppgaveService(database, syfoTilgangsKontrollClient, kafkaProducers, oppgaveService)
    val manuelloppgaveId = "1314"
    val manuellOppgave = ManuellOppgave(
        receivedSykmelding = receivedSykmelding(manuelloppgaveId, generateSykmelding()),
        validationResult = ValidationResult(Status.OK, emptyList()),
        apprec = objectMapper.readValue(
            Apprec::class.java.getResourceAsStream("/apprecOK.json").readBytes().toString(
                Charsets.UTF_8
            )
        )
    )
    val oppgaveid = 308076319

    beforeEachTest {
        clearMocks(syfoTilgangsKontrollClient, msGraphClient, kafkaProducers, oppgaveService)
        database.opprettManuellOppgave(manuellOppgave, manuellOppgave.apprec, oppgaveid)
        coEvery { syfoTilgangsKontrollClient.sjekkVeiledersTilgangTilPersonViaAzure(any(), any()) } returns Tilgang(true)
    }
    afterEachTest {
        database.connection.dropData()
    }

    describe("Autentiseringstest for api") {
        val config = Environment(
            mountPathVault = "",
            databaseName = "",
            syfosmmanuellbackendDBURL = "url",
            syfosmmanuellUrl = "https://syfosmmanuell",
            syfotilgangskontrollScope = "scope",
            oppgavebehandlingUrl = "oppgave",
            syfoTilgangsKontrollClientUrl = "http://syfotilgangskontroll",
            msGraphApiScope = "http://ms.graph.fo/",
            msGraphApiUrl = "http://ms.graph.fo.ton/",
            azureTokenEndpoint = "http://ms.token/",
            azureAppClientSecret = "secret",
            azureAppClientId = "clientId"
        )
        with(TestApplicationEngine()) {
            start()
            application.setupAuth(config, jwkProvider, "https://sts.issuer.net/myid")
            application.routing {
                authenticate("jwt") {
                    hentManuellOppgaver(manuellOppgaveService, authorizationService)
                }
            }
            application.install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                }
            }
            application.install(StatusPages) {
                exception<Throwable> { cause ->
                    call.respond(HttpStatusCode.InternalServerError, cause.message ?: "Unknown error")
                    log.error("Caught exception", cause)
                    throw cause
                }
            }
            it("Aksepterer gyldig JWT med riktig audience") {

                with(
                    handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/$oppgaveid") {
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "clientId")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.OK
                    objectMapper.readValue<ManuellOppgaveDTO>(response.content!!).oppgaveid shouldBeEqualTo oppgaveid
                }
            }
            it("Gyldig JWT med feil audience gir Unauthorized") {
                with(
                    handleRequest(HttpMethod.Get, "/api/v1/manuellOppgave/$oppgaveid") {
                        addHeader(HttpHeaders.Authorization, "Bearer ${generateJWT("2", "annenClientId")}")
                    }
                ) {
                    response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                    response.content shouldBeEqualTo null
                }
            }
        }
    }
})
