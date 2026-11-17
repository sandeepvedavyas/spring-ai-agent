package com.sandeepvedavyas.tripagent.agent

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sandeepvedavyas.tripagent.catalog.ActivityCatalog
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItineraryAssemblerTest {
    private val catalog = ActivityCatalog(jacksonObjectMapper())
    private val assembler = ItineraryAssembler(catalog)

    @Test
    fun `reprices known activities and drops unknown or sold out ids`() {
        val draft = ModelPlan(
            answer = "Lisbon with the kids.",
            participants = 2,
            itinerary = listOf(
                ModelDay(
                    day = 2,
                    date = "2026-06-13",
                    activities = listOf(ModelActivity("lis-belem-tower", "tower")),
                ),
                ModelDay(
                    day = 1,
                    date = "2026-06-12",
                    activities = listOf(
                        ModelActivity("lis-oceanario", "rain plan"),
                        ModelActivity("not-a-real-activity", "nope"),
                    ),
                ),
            ),
            crossSellIds = listOf("lis-tram-28", "missing-cross"),
        )

        val plan = assembler.assemble(draft, fallbackAnswer = "", participantsHint = 2)

        assertThat(plan.days.map { it.day }).containsExactly(1)
        val stop = plan.days.single().activities.single()
        assertThat(stop.activity.id).isEqualTo("lis-oceanario")
        assertThat(stop.quote.total).isEqualByComparingTo(BigDecimal("56.00"))
        assertThat(stop.reassurance).contains("Kid friendly: yes").contains("Rain: covered")
        assertThat(plan.cartLines).hasSize(1)
        assertThat(plan.warnings.joinToString()).contains("lis-belem-tower").contains("not-a-real-activity")
        assertThat(plan.crossSellIds).contains("lis-tram-28").doesNotContain("missing-cross", "lis-oceanario")
    }

    @Test
    fun `plain text without json becomes the answer and an empty cart`() {
        val plan = assembler.assemble(null, "The model talked instead of booking.", 2)
        assertThat(plan.answer).contains("talked")
        assertThat(plan.days).isEmpty()
        assertThat(plan.cartLines).isEmpty()
    }
}
