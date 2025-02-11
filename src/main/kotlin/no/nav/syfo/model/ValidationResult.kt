package no.nav.syfo.model

import java.time.OffsetDateTime

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>,
    val timestamp: OffsetDateTime?
)

data class RuleInfo(
    val ruleName: String,
    val messageForSender: String,
    val messageForUser: String,
    val ruleStatus: Status
)

enum class Status {
    OK,
    MANUAL_PROCESSING,
    INVALID
}
