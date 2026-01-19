package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.ApplicationRequest
import no.nav.syfo.objectMapper
import java.io.IOException
import java.net.URISyntaxException
import no.nav.syfo.sikkerlogg

@Throws(IOException::class, URISyntaxException::class)
fun getAccessTokenFromAuthHeader(request: ApplicationRequest): String {
    val authHeader = request.parseAuthorizationHeader() ?: throw UnauthorizedException()
    val accessToken = (authHeader as HttpAuthHeader.Single).blob
    logJwtClaims(accessToken)
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


fun logJwtClaims(token: String) {
    val claims = JWT.decode(token).claims.mapValues { it.value.asString() }
    sikkerlogg.info("Claims from jwt ${objectMapper.writeValueAsString(claims)}")
}