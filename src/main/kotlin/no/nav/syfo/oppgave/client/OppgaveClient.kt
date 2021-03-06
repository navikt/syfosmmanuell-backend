package no.nav.syfo.oppgave.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.oppgave.FerdigstillOppgave
import no.nav.syfo.oppgave.OpprettOppgave
import no.nav.syfo.oppgave.OpprettOppgaveResponse

@KtorExperimentalAPI
class OppgaveClient(
    private val url: String,
    private val oidcClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun opprettOppgave(opprettOppgave: OpprettOppgave, msgId: String):
        OpprettOppgaveResponse = retry("create_oppgave") {
        val response = httpClient.post<HttpStatement>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = opprettOppgave
        }.execute()
        if (response.status == HttpStatusCode.Created) {
            log.info("Opprettet oppgave for msgId $msgId")
            return@retry response.call.response.receive<OpprettOppgaveResponse>()
        } else {
            log.error("Noe gikk galt ved oppretting av oppgave for msgId $msgId: ${response.status}")
            throw RuntimeException("Noe gikk galt ved oppretting av oppgave for msgId $msgId: ${response.status}")
        }
    }

    suspend fun ferdigstillOppgave(ferdigstilloppgave: FerdigstillOppgave, msgId: String): OpprettOppgaveResponse {
        val response = httpClient.patch<HttpStatement>(url + "/" + ferdigstilloppgave.id) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            body = ferdigstilloppgave
        }.execute()
        if (response.status == HttpStatusCode.OK || response.status == HttpStatusCode.Conflict) {
            log.info("Ferdigstilt oppgave med id ${ferdigstilloppgave.id}")
            return response.call.response.receive<OpprettOppgaveResponse>()
        } else {
            log.error("Noe gikk galt ved ferdigstilling av oppgave med id ${ferdigstilloppgave.id}: ${response.status}")
            throw RuntimeException("Noe gikk galt ved ferdigstilling av oppgave med id ${ferdigstilloppgave.id}: ${response.status}")
        }
    }

    suspend fun hentOppgave(oppgaveId: Int, msgId: String): OpprettOppgaveResponse {
        val response = httpClient.get<HttpStatement>("$url/$oppgaveId") {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
        }.execute()
        if (response.status == HttpStatusCode.OK) {
            log.info("Hentet oppgave med id $oppgaveId")
            return response.call.response.receive<OpprettOppgaveResponse>()
        } else {
            log.error("Noe gikk galt ved henting av oppgave med id $oppgaveId: ${response.status}")
            throw RuntimeException("Noe gikk galt ved henting av oppgave med id $oppgaveId: ${response.status}")
        }
    }
}
