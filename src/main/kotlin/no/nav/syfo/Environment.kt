package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig
import no.nav.syfo.util.getFileAsString

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmanuell-backend"),
        val syfosmmanuellbackendDBURL: String = getEnvVar("SYFOSMMANUELL_BACKEND_DB_URL"),
        val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
        val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmmanuell-backend"),
        override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val syfoSmManuellTopic: String = getEnvVar("KAFKA_SYFO_SM_MANUELL_TOPIC", "privat-syfo-sm2013-manuell"),
        val sm2013Apprec: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-apprec-v1"),
        val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
        val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
        val syfosmmanuellUrl: String = getEnvVar("SYFOSMMANUELL_URL"),
        val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
        val syfoserviceQueueName: String = getEnvVar("MQ_SYFOSERVICE_QUEUE_NAME"),
        val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL", "http://oppgave/api/v1/oppgaver"),
        val syfoTilgangsKontrollClientUrl: String = getEnvVar("SYFOTILGANGSKONTROLL_URL", "http://syfo-tilgangskontroll/syfo-tilgangskontroll"),
        val jwkKeysUrl: String = getEnvVar("JWKKEYS_URL", "https://login.microsoftonline.com/common/discovery/keys"),
        val jwtIssuer: String = getEnvVar("JWT_ISSUER"),

        val serviceuserUsername: String = getEnvVar("SERVICE_USER_USERNAME", "/secrets/serviceuser/username"),
        val serviceuserPassword: String = getEnvVar("SERVICE_USER_PASSWORD", "/secrets/serviceuser/password"),
        val mqUsername: String = getEnvVar("MQ_USERNAME", "/secrets/default/mqUsername"),
        val mqPassword: String = getEnvVar("MQ_PASSWORD", "/secrets/default/mqPassword"),
        val oidcWellKnownUri: String = getEnvVar("OID_WELL_KNOWN_URI", "/secrets/default/oidcWellKnownUri"),
        val securityTokenUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service/rest/v1/sts/token"),
        val syfosmmanuellBackendClientId: String = getEnvVar("SYFOSMMANUELL_BACKEND_CLIENT_ID", "/secrets/azuread/syfosmmanuell-backend/client_id"),

        val disalbeKafkaSSL: String = getEnvVar("DISABLE_KAFKA_SSL", "NO"),

        override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
        override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
        override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
        override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME")
) : MqConfig, KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String,
    val oidcWellKnownUri: String,
    val syfosmmanuellBackendClientId: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
