package no.nav.syfo.unleash

import io.getunleash.DefaultUnleash
import io.getunleash.util.UnleashConfig

private val defaultConfig =
    UnleashConfig.builder()
        .appName("syfosmmanuell-backend")
        .instanceId(System.getenv("HOSTNAME"))
        .unleashAPI("${System.getenv("UNLEASH_SERVER_API_URL")}/api")
        .apiKey(System.getenv("UNLEASH_SERVER_API_TOKEN"))
        .environment(System.getenv("UNLEASH_SERVER_API_ENV"))
        .synchronousFetchOnInitialisation(true)
        .build()

fun getUnleash(config: UnleashConfig = defaultConfig) = DefaultUnleash(config)
