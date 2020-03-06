package no.nav.syfo.util

import java.util.Properties
import no.nav.syfo.Environment

fun setSecurityProtocol(environment: Environment, properties: Properties): Properties {
    if (environment.developmentMode) {
        properties.setProperty("security.protocol", "SASL_PLAINTEXT")
    }
    return properties
}
