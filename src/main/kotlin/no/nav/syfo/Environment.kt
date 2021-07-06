package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.util.getFileAsString

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmanuell-backend"),
    val syfosmmanuellbackendDBURL: String = getEnvVar("SYFOSMMANUELL_BACKEND_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmmanuell-backend"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfoSmManuellTopic: String = getEnvVar("KAFKA_SYFO_SM_MANUELL_TOPIC", "privat-syfo-sm2013-manuell"),
    val sm2013Apprec: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-apprec-v1"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013OpppgaveTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val syfosmmanuellUrl: String = getEnvVar("SYFOSMMANUELL_URL"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val syfoTilgangsKontrollClientUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL"),
    val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val smSyfoserviceMqTopic: String = "privat-syfo-syfoservice-mq",
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL"),
    val syfotilgangskontrollClientId: String = getEnvVar("SYFOTILGANGSKONTROLL_CLIENT_ID"),
    val brukernotifikasjonBeskjedTopic: String = "aapen-brukernotifikasjon-nyBeskjed-v1",
    val developmentMode: Boolean = getEnvVar("DEVELOPMENT_MODE", "false").toBoolean()
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val oidcWellKnownUri: String = getFileAsString("/secrets/default/oidcWellKnownUri"),
    val syfosmmanuellBackendClientId: String = getFileAsString("/secrets/azuread/syfosmmanuell-backend/client_id"),
    val syfosmmanuellBackendClientSecret: String = getFileAsString("/secrets/azuread/syfosmmanuell-backend/client_secret")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
