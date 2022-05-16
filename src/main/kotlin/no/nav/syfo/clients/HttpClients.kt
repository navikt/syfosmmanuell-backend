package no.nav.syfo.clients

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.clients.exception.ServiceUnavailableException
import no.nav.syfo.oppgave.client.OppgaveClient
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

class HttpClients(env: Environment) {

    companion object {
        val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
            engine {
                socketTimeout = 40_000
                connectTimeout = 40_000
                connectionRequestTimeout = 40_000
            }
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            expectSuccess = false
            HttpResponseValidator {
                handleResponseExceptionWithRequest { exception, _ ->
                    when (exception) {
                        is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                    }
                }
            }
        }
    }

    private val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    private val httpClientWithProxy = HttpClient(Apache, proxyConfig)
    private val httpClient = HttpClient(Apache, config)

    private val azureAdV2Client = AzureAdV2Client(
        azureAppClientId = env.azureAppClientId,
        azureAppClientSecret = env.azureAppClientSecret,
        azureTokenEndpoint = env.azureTokenEndpoint,
        httpClient = httpClientWithProxy
    )

    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, azureAdV2Client, httpClient, env.oppgaveScope)

    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )

    val msGraphClient = MSGraphClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )
}
