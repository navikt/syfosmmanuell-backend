package no.nav.syfo.clients

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoTilgangsKontrollClient
import no.nav.syfo.client.Tilgang
import no.nav.syfo.oppgave.client.OppgaveClient
import org.apache.http.impl.conn.SystemDefaultRoutePlanner

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
    private val aadCache: Cache<Map<String, String>, String> = Caffeine.newBuilder()
        .expireAfterWrite(50, TimeUnit.MINUTES)
        .maximumSize(100)
        .build<Map<String, String>, String>()
    @KtorExperimentalAPI
    val accessTokenClient = AccessTokenClient(
        env.aadAccessTokenUrl,
        vaultSecrets.syfosmmanuellBackendClientId,
        vaultSecrets.syfosmmanuellBackendClientSecret,
        httpClientWithProxy,
        aadCache
    )

    private val syfoTilgangskontrollCache: Cache<Map<String, String>, Tilgang> = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(100)
        .build<Map<String, String>, Tilgang>()
    @KtorExperimentalAPI
    val syfoTilgangsKontrollClient = SyfoTilgangsKontrollClient(
        url = env.syfoTilgangsKontrollClientUrl,
        httpClient = httpClient,
        syfotilgangskontrollClientId = env.syfotilgangskontrollClientId,
        accessTokenClient = accessTokenClient,
        syfoTilgangskontrollCache = syfoTilgangskontrollCache
    )
}
