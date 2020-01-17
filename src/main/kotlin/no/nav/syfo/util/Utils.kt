package no.nav.syfo.util

import io.ktor.auth.parseAuthorizationHeader
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.request.ApplicationRequest
import java.io.IOException
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

@Throws(IOException::class, URISyntaxException::class)

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)

fun getAccessTokenFromAuthHeader(request: ApplicationRequest): String? {
    val authHeader = request.parseAuthorizationHeader()
    var accessToken: String? = null
    if (!(authHeader == null ||
                authHeader !is HttpAuthHeader.Single ||
                authHeader.authScheme != "Bearer")
    ) {
        accessToken = authHeader.blob
    }
    return accessToken
}
