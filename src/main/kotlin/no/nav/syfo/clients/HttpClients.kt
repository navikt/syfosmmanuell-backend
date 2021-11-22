package no.nav.syfo.clients

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.oppgave.client.OppgaveClient
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

class HttpClients(env: Environment, vaultSecrets: VaultSecrets) {
    private val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
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

    @KtorExperimentalAPI
    val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword, env.securityTokenUrl)
    @KtorExperimentalAPI
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)

    private val azureAdV2Client = AzureAdV2Client(
        azureAppClientId = env.azureAppClientId,
        azureAppClientSecret = env.azureAppClientSecret,
        azureTokenEndpoint = env.azureTokenEndpoint,
        httpClient = httpClientWithProxy
    )

    @KtorExperimentalAPI
    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )

    @KtorExperimentalAPI
    val msGraphClient = MSGraphClient(
        environment = env,
        azureAdV2Client = azureAdV2Client,
        httpClient = httpClientWithProxy
    )
}
