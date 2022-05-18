package no.nav.syfo.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import no.nav.syfo.log
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector
import java.net.URL

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun getWellKnown(wellKnownUrl: String) =
    runBlocking { HttpClient(Apache, proxyConfig).get(URL(wellKnownUrl)).body<WellKnown>().also { log.info("Hentet wellknown") } }

@JsonIgnoreProperties(ignoreUnknown = true)
data class WellKnown(
    val authorization_endpoint: String,
    val token_endpoint: String,
    val jwks_uri: String,
    val issuer: String
)
