package com.sandeepvedavyas.tripagent.agent

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ModelPlanParserTest {
    private val parser = ModelPlanParser(
        jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES),
    )

    @Test
    fun `reads a fenced json object`() {
        val raw = """
            Here you go:
            ```json
            {"answer":"Lisbon","participants":3,"itinerary":[{"day":1,"date":"2026-06-12","activities":[{"activityId":"lis-oceanario","rationale":"kids"}]}]}
            ```
        """.trimIndent()
        val plan = parser.parse(raw)
        assertThat(plan?.answer).isEqualTo("Lisbon")
        assertThat(plan?.participants).isEqualTo(3)
        assertThat(plan?.itinerary?.single()?.activities?.single()?.activityId).isEqualTo("lis-oceanario")
    }

    @Test
    fun `returns null when there is no object`() {
        assertThat(parser.parse("I need a city first.")).isNull()
    }
}
