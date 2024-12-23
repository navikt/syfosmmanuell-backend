package no.nav.syfo.oppgave.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.syfo.azuread.v2.AzureAdV2Client
import no.nav.syfo.logger
import no.nav.syfo.oppgave.*
import no.nav.syfo.util.retry

class OppgaveClient(
    private val url: String,
    private val azureAdV2Client: AzureAdV2Client,
    private val httpClient: HttpClient,
    private val scope: String,
    private val cluster: String,
) {
    suspend fun opprettOppgave(
        opprettOppgave: OpprettOppgave,
        msgId: String
    ): OpprettOppgaveResponse =
        retry("create_oppgave") {
            val response =
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    val token = azureAdV2Client.getAccessToken(scope)
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-ID", msgId)
                    setBody(opprettOppgave)
                }
            if (response.status == HttpStatusCode.Created) {
                logger.info("Opprettet oppgave for msgId $msgId")
                return@retry response.body<OpprettOppgaveResponse>()
            } else {
                logger.error(
                    "Noe gikk galt ved oppretting av oppgave for msgId $msgId: ${response.status}"
                )
                throw RuntimeException(
                    "Noe gikk galt ved oppretting av oppgave for msgId $msgId: ${response.status}"
                )
            }
        }

    suspend fun ferdigstillOppgave(
        ferdigstilloppgave: FerdigstillOppgave,
        msgId: String
    ): OpprettOppgaveResponse {
        val response =
            httpClient.patch(url + "/" + ferdigstilloppgave.id) {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
                setBody(ferdigstilloppgave)
            }

        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Conflict) {
            return response.body<OpprettOppgaveResponse>()
        } else if (cluster == "dev-gcp" && ferdigstilloppgave.mappeId == null) {
            logger.info(
                "Skipping ferdigstilt oppgave med in dev due to mappeId is null id ${ferdigstilloppgave.id}: ${response.status}"
            )
            return OpprettOppgaveResponse(ferdigstilloppgave.id, ferdigstilloppgave.versjon)
        } else {
            logger.error(
                "Noe gikk galt ved ferdigstilling av oppgave med id ${ferdigstilloppgave.id}: ${response.status}: ${response.body<String>()}"
            )
            throw RuntimeException(
                "Noe gikk galt ved ferdigstilling av oppgave med id ${ferdigstilloppgave.id}: ${response.status}"
            )
        }
    }

    suspend fun endreOppgave(endreOppgave: EndreOppgave, msgId: String): OpprettOppgaveResponse {
        val response =
            httpClient.patch(url + "/" + endreOppgave.id) {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
                setBody(endreOppgave)
            }

        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Conflict) {
            return response.body<OpprettOppgaveResponse>()
        } else if (cluster == "dev-gcp" && endreOppgave.mappeId == null) {
            logger.info(
                "Skipping endring av oppgave med in dev due to mappeId is null id ${endreOppgave.id}: ${response.status}"
            )
            return OpprettOppgaveResponse(endreOppgave.id, endreOppgave.versjon)
        } else {
            logger.error(
                "Noe gikk galt ved endring av oppgave med id ${endreOppgave.id}: ${response.status}"
            )
            throw RuntimeException(
                "Noe gikk galt ved endring av oppgave med id ${endreOppgave.id}: ${response.status}"
            )
        }
    }

    suspend fun hentOppgave(oppgaveId: Int, msgId: String): OpprettOppgaveResponse? {
        val response =
            httpClient.get("$url/$oppgaveId") {
                contentType(ContentType.Application.Json)
                val token = azureAdV2Client.getAccessToken(scope)
                header("Authorization", "Bearer $token")
                header("X-Correlation-ID", msgId)
            }
        if (response.status == HttpStatusCode.OK) {
            return response.body<OpprettOppgaveResponse>()
        } else if (response.status == HttpStatusCode.NotFound) {
            return null
        } else {
            logger.error(
                "Noe gikk galt ved henting av oppgave med id $oppgaveId: ${response.status}"
            )
            throw RuntimeException(
                "Noe gikk galt ved henting av oppgave med id $oppgaveId: ${response.status}"
            )
        }
    }
}
