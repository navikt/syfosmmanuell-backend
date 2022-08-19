package no.nav.syfo.util

import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.ApplicationRequest
import java.io.IOException
import java.net.URISyntaxException

@Throws(IOException::class, URISyntaxException::class)

fun getAccessTokenFromAuthHeader(request: ApplicationRequest): String {
    val authHeader = request.parseAuthorizationHeader()
        ?: throw UnauthorizedException()
    return (authHeader as HttpAuthHeader.Single).blob
}

class UnauthorizedException : Exception()
