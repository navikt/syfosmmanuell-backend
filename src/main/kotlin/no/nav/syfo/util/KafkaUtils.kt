package no.nav.syfo.util

import no.nav.syfo.Environment
import java.util.Properties

fun setSecurityProtocol (environment: Environment, properties: Properties): Properties {
    if (environment.developmentMode) {
        properties.setProperty("security.protocol", "SASL_PLAINTEXT")
    }
    return properties
}