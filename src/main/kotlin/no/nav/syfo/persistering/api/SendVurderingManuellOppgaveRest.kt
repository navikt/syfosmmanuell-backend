package no.nav.syfo.persistering.api

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.authorization.service.AuthorizationService
import no.nav.syfo.log
import no.nav.syfo.model.Merknad
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.service.ManuellOppgaveService
import no.nav.syfo.util.getAccessTokenFromAuthHeader

@KtorExperimentalAPI
fun Route.sendVurderingManuellOppgave(
    manuellOppgaveService: ManuellOppgaveService,
    authorizationService: AuthorizationService
) {
    route("/api/v1") {
        post("/vurderingmanuelloppgave/{oppgaveid}") {
            val oppgaveId = call.parameters["oppgaveid"]!!.toInt()
            log.info("Mottok kall til /api/v1/vurderingmanuelloppgave/$oppgaveId")
            val accessToken = getAccessTokenFromAuthHeader(call.request)
            val navEnhet = call.request.headers["X-Nav-Enhet"]

            val hasAccess = authorizationService.hasAccess(oppgaveId, accessToken)

            if (navEnhet.isNullOrEmpty()) {
                log.error("Mangler X-Nav-Enhet i http header")
                call.respond(HttpStatusCode.BadRequest, "Mangler X-Nav-Enhet i http header")
                return@post
            }

            when (hasAccess) {
                false -> {
                    call.respond(HttpStatusCode.NotFound)
                }
                true -> {
                    val result = call.receive<Result>()

                    val validationResult = result.tilValidationResult()

                    val merknad = result.tilMerknad()

                    val veileder = authorizationService.getVeileder(accessToken)

                    manuellOppgaveService.ferdigstillManuellBehandling(
                        oppgaveId = oppgaveId,
                        enhet = navEnhet,
                        veileder = veileder,
                        validationResult = validationResult,
                        accessToken = accessToken,
                        merknader = if (merknad != null) listOf(merknad) else null
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

// TODO: remove when refactoring tests
enum class RuleInfoTekst(val messageForUser: String, val messageForSender: String, val rulename: String) {
    MANGLER_BEGRUNNELSE(
        messageForUser = "Sykmeldingen din starter før du oppsøkte behandleren, uten at det er gitt en god nok begrunnelse for dette.",
        messageForSender = "Sykmelding gjelder som hovedregel fra den dagen pasienten oppsøker behandler. Sykmeldingen er tilbakedatert uten at det kommer tydelig nok fram hvorfor dette var nødvendig. Sykmeldingen er derfor avvist, og det må skrives en ny hvis det fortsatt er aktuelt med sykmelding. Pasienten har fått beskjed om å vente på ny sykmelding fra deg.",
        rulename = "TILBAKEDATERT_MANGLER_BEGRUNNELSE"
    ),
    UGYLDIG_TILBAKEDATERING(
        messageForUser = "Sykmeldingen din starter før du oppsøkte behandleren, uten at det er gitt en god nok begrunnelse for dette.",
        messageForSender = "Sykmelding gjelder som hovedregel fra den dagen pasienten oppsøker behandler. Sykmeldingen er tilbakedatert uten at det kommer tydelig nok fram hvorfor dette var nødvendig. Sykmeldingen er derfor avvist, og det må skrives en ny hvis det fortsatt er aktuelt med sykmelding. Pasienten har fått beskjed om å vente på ny sykmelding fra deg.",
        rulename = "UGYLDIG_TILBAKEDATERING"
    )
}

enum class MerknadType {
    UGYLDIG_TILBAKEDATERING,
    KREVER_FLERE_OPPLYSNINGER
}

enum class AvvisningType {
    MANGLER_BEGRUNNELSE,
    UGYLDIG_BEGRUNNELSE
}

enum class ResultStatus {
    GODKJENT,
    GODKJENT_MED_MERKNAD,
    AVVIST
}

data class Result(
    val status: ResultStatus,
    val merknad: MerknadType?,
    val avvisningtype: AvvisningType?,
) {
    fun toMerknad(): Merknad? {
        return when (status) {
            ResultStatus.GODKJENT_MED_MERKNAD -> {
                return when (merknad) {
                    MerknadType.UGYLDIG_TILBAKEDATERING -> {
                        Merknad(
                            type=MerknadType.UGYLDIG_TILBAKEDATERING.name,
                            beskrivelse="Tilbakedateringen er vurdert som ugyldig, men brukeren får allikevel sende søknad slik at et vedtak kan fattes"
                        )
                    }
                    MerknadType.KREVER_FLERE_OPPLYSNINGER -> {
                        Merknad(
                            type=MerknadType.KREVER_FLERE_OPPLYSNINGER.name,
                            beskrivelse=""
                        )
                    }
                    else -> {
                        throw RuntimeException("Result with status GODKJENT_MED_MERKNAD missing merknad property")
                    }
                }
            },
            else -> null
        }
    }

    fun toValidationResult(): ValidationResult {
        return when (status) {
            ResultStatus.GODKJENT -> ValidationResult(Status.OK, emptyList())
            ResultStatus.GODKJENT_MED_MERKNAD -> ValidationResult(Status.OK, emptyList())
            ResultStatus.AVVIST -> {
                return when (avvisningtype) {
                    AvvisningType.MANGLER_BEGRUNNELSE -> {
                        ValidationResult(
                            Status.INVALID,
                            listOf(
                                RuleInfo(
                                    ruleName="TILBAKEDATERT_MANGLER_BEGRUNNELSE",
                                    messageForSender="",
                                    messageForUser="",
                                    Status.INVALID
                                )
                            )
                        )
                    },
                    AvvisningType.UGYLDIG_BEGRUNNELSE -> {
                        ValidationResult(
                            Status.INVALID,
                            listOf(
                                RuleInfo(
                                    ruleName="TILBAKEDATERT_MANGLER_BEGRUNNELSE",
                                    messageForSender="",
                                    messageForUser="",
                                    Status.INVALID
                                )
                            )
                        )
                    }
                    else -> {
                        throw RuntimeException("Result with status AVVIST missing avvisningtype property")
                    }
                }
            }
        }
    }
}
