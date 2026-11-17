package com.sandeepvedavyas.tripagent.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlanApiIntegrationTest @Autowired constructor(
    private val rest: TestRestTemplate,
) {
    @Test
    fun `plan runs a tool call round trip and prices the catalog`() {
        val before = dispatcher.requests.size
        val response = rest.exchange(
            "/api/v1/plan",
            HttpMethod.POST,
            entity("""{"message":"3 days in Lisbon with kids, mid budget","sessionId":"plan-1","conversationId":"plan-conv"}"""),
            String::class.java,
        )
        assertThat(response.statusCode.value()).isEqualTo(200)
        val body = mapper.readTree(response.body)
        assertThat(body["disclaimer"].asText()).contains("GetYourGuide")
        assertThat(body["answer"].asText()).contains("Lisbon")
        assertThat(body["model"].asText()).isEqualTo("gpt-4o-mini")
        assertThat(body["toolsUsed"].map { it.asText() }).contains("searchActivities")
        val stops = buildList {
            val days = body["itinerary"]
            for (dayIndex in 0 until days.size()) {
                val activities = days[dayIndex]["activities"]
                for (stopIndex in 0 until activities.size()) {
                    add(activities[stopIndex])
                }
            }
        }
        assertThat(stops.map { it["activityId"].asText() }).containsExactly("lis-oceanario", "lis-belem-tower", "lis-pastel-workshop")
        assertThat(stops[0]["pricePerPerson"].decimalValue()).isEqualByComparingTo(BigDecimal("28.00"))
        assertThat(stops[0]["totalPrice"].decimalValue()).isEqualByComparingTo(BigDecimal("56.00"))
        assertThat(stops[0]["reassurance"].asText()).contains("Kid friendly: yes")
        assertThat(body["cart"]["total"].decimalValue()).isEqualByComparingTo(BigDecimal("190.00"))
        assertThat(body["crossSells"].map { it["activityId"].asText() }).contains("lis-tram-28")

        val calls = dispatcher.requests.drop(before)
        assertThat(calls).hasSizeGreaterThanOrEqualTo(2)
        assertThat(calls[0].path).isEqualTo("/v1/chat/completions")
        assertThat(calls[0].getHeader("Authorization")).isEqualTo("Bearer test-upstream-key")
        assertThat(calls[1].body).contains("lis-oceanario")
        assertThat(calls[1].body).containsPattern(""""role"\s*:\s*"tool"""")

        val cart = rest.exchange(
            "/api/v1/sessions/plan-1/cart",
            HttpMethod.GET,
            HttpEntity<Void>(apiHeaders()),
            String::class.java,
        )
        assertThat(cart.statusCode.value()).isEqualTo(200)
        assertThat(mapper.readTree(cart.body)["total"].decimalValue()).isEqualByComparingTo(BigDecimal("190.00"))
    }

    @Test
    fun `chat keeps the previous answer in memory`() {
        val first = rest.exchange(
            "/api/v1/chat",
            HttpMethod.POST,
            entity("""{"message":"One easy family day in Lisbon","sessionId":"mem-1","conversationId":"mem-conv"}"""),
            String::class.java,
        )
        assertThat(first.statusCode.value()).isEqualTo(200)
        assertThat(mapper.readTree(first.body)["answer"].asText()).contains("TICKET-ALPHA")

        val before = dispatcher.requests.size
        val second = rest.exchange(
            "/api/v1/chat",
            HttpMethod.POST,
            entity("""{"message":"What ticket code did you give me?","sessionId":"mem-1","conversationId":"mem-conv"}"""),
            String::class.java,
        )
        assertThat(second.statusCode.value()).isEqualTo(200)
        assertThat(mapper.readTree(second.body)["answer"].asText()).contains("TICKET-ALPHA")
        val followUp = dispatcher.requests.drop(before).first { it.body.contains("What ticket code") }
        assertThat(followUp.body).contains("TICKET-ALPHA")
    }

    @Test
    fun `missing api key is unauthorized and does not call the model`() {
        val before = dispatcher.requests.size
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val response = rest.exchange(
            "/api/v1/plan",
            HttpMethod.POST,
            HttpEntity("""{"message":"Lisbon"}""", headers),
            String::class.java,
        )
        assertThat(response.statusCode.value()).isEqualTo(401)
        assertThat(response.headers.contentType.toString()).contains("application/problem+json")
        assertThat(response.body).contains("API key")
        assertThat(dispatcher.requests).hasSize(before)
    }

    @Test
    fun `blank message is a validation problem`() {
        val response = rest.exchange(
            "/api/v1/plan",
            HttpMethod.POST,
            entity("""{"message":"  "}"""),
            String::class.java,
        )
        assertThat(response.statusCode.value()).isEqualTo(400)
        assertThat(response.body).contains("message")
    }

    @Test
    fun `health is public and prometheus is not`() {
        val health = rest.getForEntity("/actuator/health", String::class.java)
        assertThat(health.statusCode.value()).isEqualTo(200)
        assertThat(health.body).contains("\"status\":\"UP\"")
        assertThat(health.body).contains("GetYourGuide")

        val root = rest.getForEntity("/", String::class.java)
        assertThat(root.statusCode.value()).isEqualTo(200)
        assertThat(root.body).contains("/api/v1/plan")

        val metrics = rest.getForEntity("/actuator/prometheus", String::class.java)
        assertThat(metrics.statusCode.value()).isEqualTo(401)
    }

    private fun entity(json: String): HttpEntity<String> = HttpEntity(json, apiHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    })

    private fun apiHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.set(API_KEY_HEADER, API_KEY)
        return headers
    }

    companion object {
        private const val API_KEY = "test-agent-key"
        private const val API_KEY_HEADER = "X-API-Key"
        private val mapper: ObjectMapper = jacksonObjectMapper()
        private val dispatcher = OpenAiDispatcher()
        private val server = MockWebServer()

        init {
            server.dispatcher = dispatcher
            server.start()
        }

        @JvmStatic
        @AfterAll
        fun shutdown() {
            server.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.ai.openai.base-url") { server.url("/v1").toString().trimEnd('/') }
            registry.add("spring.ai.openai.api-key") { "test-upstream-key" }
            registry.add("spring.ai.openai.chat.options.model") { "gpt-4o-mini" }
            registry.add("agent.auth.api-key") { API_KEY }
            registry.add("agent.llm.timeout") { "5s" }
        }
    }
}

private class OpenAiDispatcher : Dispatcher() {
    val requests: MutableList<RecordedCall> = CopyOnWriteArrayList()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8()
        requests += RecordedCall(request, body)
        val toolTurn = TOOL_ROLE.containsMatchIn(body)
        val content = when {
            !toolTurn -> null
            body.contains("What ticket code") -> "The ticket code from earlier is TICKET-ALPHA."
            else -> ITINERARY
        }
        return completion(content, toolCall = !toolTurn)
    }

    private fun completion(content: String?, toolCall: Boolean): MockResponse {
        val message = linkedMapOf<String, Any?>(
            "role" to "assistant",
            "content" to content.orEmpty(),
        )
        if (toolCall) {
            message["tool_calls"] = listOf(
                mapOf(
                    "id" to "call_search_1",
                    "type" to "function",
                    "function" to mapOf(
                        "name" to "searchActivities",
                        "arguments" to """{"city":"Lisbon","category":"family","maxPrice":80,"date":"2026-06-12"}""",
                    ),
                ),
            )
        }
        val payload = mapOf(
            "id" to "chatcmpl-test",
            "object" to "chat.completion",
            "created" to 1_710_000_000,
            "model" to "gpt-4o-mini",
            "choices" to listOf(
                mapOf(
                    "index" to 0,
                    "finish_reason" to if (toolCall) "tool_calls" else "stop",
                    "message" to message,
                ),
            ),
            "usage" to mapOf(
                "prompt_tokens" to 20,
                "completion_tokens" to 12,
                "total_tokens" to 32,
            ),
        )
        return MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(RESPONSE_MAPPER.writeValueAsString(payload))
    }

    companion object {
        private val RESPONSE_MAPPER: ObjectMapper = jacksonObjectMapper()
        private val TOOL_ROLE = Regex(""""role"\s*:\s*"tool"""")
        private val ITINERARY = """
            {"answer":"Three family-friendly days in Lisbon, with an aquarium, a tower, and a pastry workshop. Reference TICKET-ALPHA.","participants":2,"itinerary":[{"day":1,"date":"2026-06-12","activities":[{"activityId":"lis-oceanario","rationale":"Rain-proof and built for kids."}]},{"day":2,"date":"2026-06-14","activities":[{"activityId":"lis-belem-tower","rationale":"Skip-the-line entry before the crowds."}]},{"day":3,"date":"2026-06-15","activities":[{"activityId":"lis-pastel-workshop","rationale":"A hands-on food stop that still fits a mid budget."}]}],"crossSellIds":["lis-tram-28"],"objectionNotes":["The aquarium is indoors if it rains."]}
        """.trimIndent()
    }
}

private class RecordedCall(
    private val request: RecordedRequest,
    val body: String,
) {
    val path: String get() = request.path ?: ""

    fun getHeader(name: String): String? = request.getHeader(name)
}
