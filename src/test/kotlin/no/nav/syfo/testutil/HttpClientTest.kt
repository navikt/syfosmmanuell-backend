package no.nav.syfo.testutil

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

data class ResponseData(val httpStatusCode: HttpStatusCode, val content: String, val headers: Headers = headersOf("Content-Type", listOf("application/json")))

fun getSerializer(): JacksonSerializer {
    return JacksonSerializer {
        registerKotlinModule()
    }
}

class HttpClientTest {

    var responseData: ResponseData? = null
    var responseDataOboToken: ResponseData? = null

    val httpClient = HttpClient(MockEngine) {
        install(JsonFeature) {
            getSerializer()
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
