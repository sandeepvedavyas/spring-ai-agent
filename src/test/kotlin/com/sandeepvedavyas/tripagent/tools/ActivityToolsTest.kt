package com.sandeepvedavyas.tripagent.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.sandeepvedavyas.tripagent.catalog.ActivityCatalog
import com.sandeepvedavyas.tripagent.catalog.CartSnapshot
import com.sandeepvedavyas.tripagent.catalog.SearchResult
import com.sandeepvedavyas.tripagent.catalog.ToolError
import com.sandeepvedavyas.tripagent.catalog.TripCart
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ToolContext

class ActivityToolsTest {
    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val catalog = ActivityCatalog(mapper)
    private val cart = TripCart()
    private val tools = ActivityTools(catalog, cart, mapper)
    private val audit = mutableListOf<String>()
    private val context = ToolContext(mapOf(ActivityTools.AUDIT_KEY to audit))

    @Test
    fun `search returns catalog json and records the tool name`() {
        val raw = tools.searchActivities("Barcelona", "museum", 20.0, "", context)
        val result = mapper.readValue<SearchResult>(raw)
        assertThat(result.activities.map { it.activityId }).contains("bcn-picasso")
        assertThat(audit).containsExactly("searchActivities")
    }

    @Test
    fun `addToCart prices the line and getCart reads it back`() {
        val added = mapper.readValue<CartSnapshot>(
            tools.addToCart("sess-1", "rom-colosseum-skip", "2026-05-02", 2, context),
        )
        assertThat(added.items).hasSize(1)
        assertThat(added.items[0].total).isEqualByComparingTo(BigDecimal("76.00"))
        assertThat(added.total).isEqualByComparingTo(BigDecimal("76.00"))

        val again = mapper.readValue<CartSnapshot>(tools.getCart("sess-1", context))
        assertThat(again.items).hasSize(1)
        assertThat(audit).contains("addToCart", "getCart")
    }

    @Test
    fun `addToCart refuses a sold out date`() {
        val raw = tools.addToCart("sess-2", "lis-belem-tower", "2026-06-13", 2, context)
        val error = mapper.readValue<ToolError>(raw)
        assertThat(error.error).contains("Sold out")
        assertThat(cart.get("sess-2").items).isEmpty()
    }

    @Test
    fun `addToCart refuses a bad session id`() {
        val raw = tools.addToCart("has space", "lis-oceanario", "2026-06-12", 2, context)
        assertThat(mapper.readValue<ToolError>(raw).error).contains("sessionId")
    }
}
