package no.nav.syfo.elector

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.syfo.log
import java.net.InetAddress

class LeaderElector(
    private val httpClient: HttpClient,
    private val electorPath: String,
) {
    suspend fun isLeader(): Boolean {
        val hostname: String = withContext(Dispatchers.IO) { InetAddress.getLocalHost() }.hostName

        return try {
            val leader = httpClient.get(getHttpPath(electorPath)).body<Leader>()
            leader.name == hostname
        } catch (e: Exception) {
            val message = "Kall mot elector feiler"
            log.warn(message)
            false
        }
    }

    private fun getHttpPath(url: String): String =
        when (url.startsWith("http://")) {
            true -> url
            else -> "http://$url"
        }

    private data class Leader(val name: String)
}
