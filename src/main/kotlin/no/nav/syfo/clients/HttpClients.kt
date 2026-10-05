package no.nav.syfo.clients

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.sockets.*
import io.ktor.serialization.jackson3.jackson
import no.nav.syfo.Environment
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.client.MSGraphClient
import no.nav.syfo.client.TexasClient
import no.nav.syfo.client.TilgangsmaskinClient
import no.nav.syfo.clients.exception.ServiceUnavailableException
import no.nav.syfo.logger
import no.nav.syfo.oppgave.client.OppgaveClient

class HttpClients(env: Environment) {

    companion object {
        val config: HttpClientConfig<Apache5EngineConfig>.() -> Unit = {
            install(ContentNegotiation) { jackson {} }
            expectSuccess = false
            HttpResponseValidator {
                handleResponseExceptionWithRequest { exception, _ ->
                    when (exception) {
                        is SocketTimeoutException ->
                            throw ServiceUnavailableException(exception.message)
                    }
                }
            }
            install(HttpRequestRetry) {
                constantDelay(50, 0, false)
                retryOnExceptionIf(3) { request, throwable ->
                    logger.warn("Caught exception ${throwable.message}, for url ${request.url}")
                    true
                }
                retryIf(maxRetries) { request, response ->
                    if (response.status.value.let { it in 500..599 }) {
                        logger.warn(
                            "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                        )
                        true
                    } else {
                        false
                    }
                }
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 20_000
            }
        }
    }

    val httpClient = HttpClient(Apache5, config)

    private val azureAdV2Client =
        AzureAdV2Client(
            azureAppClientId = env.azureAppClientId,
            azureAppClientSecret = env.azureAppClientSecret,
            azureTokenEndpoint = env.azureTokenEndpoint,
            httpClient = httpClient,
        )

    val oppgaveClient =
        OppgaveClient(
            env.oppgavebehandlingUrl,
            azureAdV2Client,
            httpClient,
            env.oppgaveScope,
            env.cluster,
        )

    val msGraphClient =
        MSGraphClient(environment = env, azureAdV2Client = azureAdV2Client, httpClient = httpClient)

    val texasClient = TexasClient(httpClient = httpClient, environment = env)

    val tilgangsmaskinClient =
        TilgangsmaskinClient(environment = env, texasClient = texasClient, httpClient = httpClient)
}
