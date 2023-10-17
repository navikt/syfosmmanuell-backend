package no.nav.syfo.auditLogger

import com.auth0.jwt.JWT
import java.time.ZonedDateTime.now

internal class AuditLogger {
    fun createcCefMessage(
        fnr: String?,
        accessToken: String,
        operation: Operation,
        requestPath: String,
        permit: Permit,
    ): String {
        val application = "syfosmmanuell-backend"
        val decodedJWT = JWT.decode(accessToken)
        val navEmail = decodedJWT.claims["preferred_username"]!!.asString()
        val now = now().toInstant().toEpochMilli()
        val subject = fnr?.padStart(11, '0')
        val duidStr = subject?.let { " duid=$it" } ?: ""

        return "CEF:0|Sykemeldingregistrering|$application|auditLog|1.0|${operation.logString}|Sporingslogg|INFO|end=$now$duidStr" +
            " suid=$navEmail request=$requestPath flexString1Label=Decision flexString1=$permit"
    }

    enum class Operation(val logString: String) {
        READ("audit:access"),
        WRITE("audit:update"),
        UNKNOWN("audit:unknown"),
    }

    enum class Permit(val logString: String) {
        PERMIT("Permit"),
        DENY("Deny"),
    }
}
