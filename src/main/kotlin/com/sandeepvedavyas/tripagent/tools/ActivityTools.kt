package com.sandeepvedavyas.tripagent.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandeepvedavyas.tripagent.catalog.ActivityCatalog
import com.sandeepvedavyas.tripagent.catalog.BadCatalogInput
import com.sandeepvedavyas.tripagent.catalog.CartLine
import com.sandeepvedavyas.tripagent.catalog.Pricing
import com.sandeepvedavyas.tripagent.catalog.ToolError
import com.sandeepvedavyas.tripagent.catalog.TripCart
import com.sandeepvedavyas.tripagent.domain.Ids
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class ActivityTools(
    private val catalog: ActivityCatalog,
    private val cart: TripCart,
    private val objectMapper: ObjectMapper,
) {
    @Tool(
        name = "searchActivities",
        description = "Search the demo catalog for bookable activities. Use category 'any' and maxPrice 0 when the traveler did not constrain them. date may be empty.",
    )
    fun searchActivities(
        @ToolParam(description = "City: Lisbon, Barcelona, Berlin, or Rome") city: String,
        @ToolParam(description = "Category such as family, food, sightseeing, museum, outdoor, cruise, workshop, nightlife, or any") category: String,
        @ToolParam(description = "Maximum price per person in EUR. Use 0 for no limit.") maxPrice: Double,
        @ToolParam(description = "Travel date yyyy-MM-dd, or empty if unknown") date: String,
        toolContext: ToolContext,
    ): String = call("searchActivities", toolContext) {
        catalog.search(city, category, maxPrice, date)
    }

    @Tool(
        name = "checkAvailability",
        description = "Check whether an activity can take the party on a specific date. Call this before adding the activity to the itinerary.",
    )
    fun checkAvailability(
        @ToolParam(description = "Catalog activity id") activityId: String,
        @ToolParam(description = "Date yyyy-MM-dd") date: String,
        @ToolParam(description = "Number of participants") participants: Int,
        toolContext: ToolContext,
    ): String = call("checkAvailability", toolContext) {
        catalog.availability(activityId, date, participants)
    }

    @Tool(
        name = "getPrice",
        description = "Authoritative price in EUR for an activity and a party size. Parties of 4 or more get 10 percent off the per-person price. Never invent a price.",
    )
    fun getPrice(
        @ToolParam(description = "Catalog activity id") activityId: String,
        @ToolParam(description = "Number of participants") participants: Int,
        toolContext: ToolContext,
    ): String = call("getPrice", toolContext) {
        catalog.price(activityId, participants)
    }

    @Tool(
        name = "summarizeReviews",
        description = "Summarize reviews and answer objections: skip-the-line, kid friendly, and rain. Call this when the traveler mentions children, queues, lines, crowds, weather, or whether an activity is worth it.",
    )
    fun summarizeReviews(
        @ToolParam(description = "Catalog activity id") activityId: String,
        toolContext: ToolContext,
    ): String = call("summarizeReviews", toolContext) {
        catalog.summarizeReviews(activityId)
    }

    @Tool(
        name = "suggestCrossSell",
        description = "Suggest up to three complementary activities for something already chosen, in the same city. Use this after the hero activity is picked to raise the booking.",
    )
    fun suggestCrossSell(
        @ToolParam(description = "Activity id already chosen or booked") bookedActivityId: String,
        toolContext: ToolContext,
    ): String = call("suggestCrossSell", toolContext) {
        catalog.crossSell(bookedActivityId)
    }

    @Tool(
        name = "addToCart",
        description = "Add one bookable activity to the traveler cart. Refuses unknown ids, bad dates, party sizes outside the activity limits, and sold-out dates. Replaces an existing line for the same activity and date.",
    )
    fun addToCart(
        @ToolParam(description = "Session id from the user message") sessionId: String,
        @ToolParam(description = "Catalog activity id") activityId: String,
        @ToolParam(description = "Date yyyy-MM-dd") date: String,
        @ToolParam(description = "Number of participants") participants: Int,
        toolContext: ToolContext,
    ): String = call("addToCart", toolContext) {
        if (!Ids.isSession(sessionId)) {
            return@call ToolError("sessionId must match ${Ids.SESSION.pattern}.")
        }
        val activity = catalog.find(activityId)
            ?: return@call ToolError("Unknown activityId $activityId.")
        val day = catalog.parseRequiredDate(date)
        Pricing.participantError(activity, participants)?.let { return@call ToolError(it) }
        val availability = catalog.availability(activity.id, day.toString(), participants)
        if (!availability.available) {
            return@call ToolError(availability.reason)
        }
        val quote = catalog.price(activity.id, participants)
        if (quote.error != null) {
            return@call ToolError(quote.error)
        }
        cart.add(
            sessionId,
            CartLine(
                activityId = activity.id,
                title = activity.title,
                city = activity.city,
                date = day.toString(),
                participants = participants,
                pricePerPerson = quote.pricePerPerson,
                total = quote.total,
                currency = quote.currency,
            ),
        )
    }

    @Tool(
        name = "getCart",
        description = "Read the current cart for a session, including line totals.",
    )
    fun getCart(
        @ToolParam(description = "Session id from the user message") sessionId: String,
        toolContext: ToolContext,
    ): String = call("getCart", toolContext) {
        if (!Ids.isSession(sessionId)) {
            ToolError("sessionId must match ${Ids.SESSION.pattern}.")
        } else {
            cart.get(sessionId)
        }
    }

    private fun call(name: String, toolContext: ToolContext, body: () -> Any): String {
        record(toolContext, name)
        val payload = try {
            body()
        } catch (ex: BadCatalogInput) {
            ToolError(ex.message ?: "Invalid tool input.")
        }
        return objectMapper.writeValueAsString(payload)
    }

    private fun record(toolContext: ToolContext, name: String) {
        val audit = toolContext.context[AUDIT_KEY]
        if (audit is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            (audit as MutableList<String>).add(name)
        }
    }

    companion object {
        const val AUDIT_KEY: String = "toolAudit"
    }
}
