package no.nav.syfo.util

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.testutil.Claim
import no.nav.syfo.testutil.generateJWT

class UtilsTest :
    FunSpec({
        context("Log NAV Epost from token when no access") {
            test("should not throw excpetion when has claim preferred_username") {
                val token =
                    generateJWT(
                        "2",
                        "clientId",
                        Claim("preferred_username", "firstname.lastname@nav.no")
                    )!!
                shouldNotThrow<Exception> {
                    logNAVEpostFromTokenWhenNoAccessToSecureLogs(token, "/thispath")
                }
            }

            test("should not throw excpetion when missing claim preferred_username") {
                val token = generateJWT("2", "clientId")!!
                shouldNotThrow<Exception> {
                    logNAVEpostFromTokenWhenNoAccessToSecureLogs(token, "/thispath")
                }
            }
        }
    })
