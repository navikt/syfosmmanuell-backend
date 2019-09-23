package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val syfoSmManuellTopic: String = getEnvVar("KAFKA_SYFO_SM_MANUELL_TOPIC", "privat-syfo-sm2013-manuell"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmanuell-backend"),
    val syfosmmanuellbackendDBURL: String = getEnvVar("SYFOSMMANUELL_BACKEND_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "syfosmmanuell-backend"),
    val sm2013Apprec: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-apprec-v1"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val norg2V1EndpointURL: String = getEnvVar("NORG2_V1_ENDPOINT_URL"),
    val arbeidsfordelingV1EndpointURL: String = getEnvVar("ARBEIDSFORDELING_V1_ENDPOINT_URL"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL")
) : KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
