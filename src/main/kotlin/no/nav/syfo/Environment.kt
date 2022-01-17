package no.nav.syfo

import no.nav.syfo.util.getFileAsString

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmanuell-backend"),
    val syfosmmanuellbackendDBURL: String = getEnvVar("SYFOSMMANUELL_BACKEND_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmmanuell-backend"),
    val manuellTopic: String = "teamsykmelding.sykmelding-manuell",
    val apprecTopic: String = "teamsykmelding.sykmelding-apprec",
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val produserOppgaveTopic: String = "teamsykmelding.oppgave-produser-oppgave",
    val syfosmmanuellUrl: String = getEnvVar("SYFOSMMANUELL_URL"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val syfoTilgangsKontrollClientUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val smSyfoserviceMqTopic: String = "teamsykmelding.syfoservice-mq",
    val syfotilgangskontrollScope: String = getEnvVar("SYFOTILGANGSKONTROLL_SCOPE"),
    val msGraphApiScope: String = getEnvVar("MS_GRAPH_API_SCOPE"),
    val msGraphApiUrl: String = getEnvVar("MS_GRAPH_API_URL"),
    val azureTokenEndpoint: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val azureAppClientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val azureAppClientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET")
)

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val oidcWellKnownUri: String = getFileAsString("/secrets/default/oidcWellKnownUri"),
    val syfosmmanuellBackendClientId: String = getFileAsString("/secrets/azuread/syfosmmanuell-backend/client_id"),
    val syfosmmanuellBackendClientSecret: String = getFileAsString("/secrets/azuread/syfosmmanuell-backend/client_secret")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
