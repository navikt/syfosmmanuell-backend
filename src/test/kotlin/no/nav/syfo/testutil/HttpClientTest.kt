package no.nav.syfo.testutil

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson

data class ResponseData(val httpStatusCode: HttpStatusCode, val content: String, val headers: Headers = headersOf("Content-Type", listOf("application/json")))

class HttpClientTest {

    var responseData: ResponseData? = null
    var responseDataOboToken: ResponseData? = null

    val httpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            addHandler { request ->
                if (request.url.host == "obo") {
                    respond(responseDataOboToken!!.content, responseDataOboToken!!.httpStatusCode, responseDataOboToken!!.headers)
                } else {
                    respond(responseData!!.content, responseData!!.httpStatusCode, responseData!!.headers)
                }
            }
        }
    }
}
