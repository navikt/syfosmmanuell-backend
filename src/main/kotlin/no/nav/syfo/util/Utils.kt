package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import no.nav.syfo.sikkerlogg
import java.io.IOException
import java.net.URISyntaxException

@Throws(IOException::class, URISyntaxException::class)
fun getAccessTokenFromAuthHeader(request: ApplicationRequest): String {
    val authHeader = request.parseAuthorizationHeader() ?: throw UnauthorizedException()
    val accessToken = (authHeader as HttpAuthHeader.Single).blob
    return accessToken
}

class UnauthorizedException : Exception()

fun logNAVEpostFromTokenWhenNoAccessToSecureLogs(token: String, path: String) {
    try {
        val decodedJWT = JWT.decode(token)
        val navEpost = decodedJWT.claims["preferred_username"]?.asString()

        sikkerlogg.info("Logger ut navEpost: {}, har ikke tilgang til path: {}", navEpost, path)
    } catch (exception: Exception) {
        sikkerlogg.info("Fikk ikkje hentet ut navEpost", exception)
    }
}