package com.sandeepvedavyas.tripagent.api

import com.sandeepvedavyas.tripagent.catalog.CartSnapshot
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class PlanRequest(
    @field:NotBlank
    @field:Size(max = 4000)
    val message: String,
    @field:Pattern(regexp = "[A-Za-z0-9._-]{1,80}")
    val sessionId: String? = null,
    @field:Pattern(regexp = "[A-Za-z0-9._-]{1,80}")
    val conversationId: String? = null,
    @field:Min(1)
    @field:Max(15)
    val participants: Int? = null,
)

data class PlanResponse(
    val sessionId: String,
    val conversationId: String,
    val answer: String,
    val model: String?,
    val itinerary: List<DayResponse>,
    val cart: CartResponse,
    val crossSells: List<CrossSellResponse>,
    val objectionNotes: List<String>,
    val toolsUsed: List<String>,
    val warnings: List<String>,
    val disclaimer: String,
)

data class DayResponse(
    val day: Int,
    val date: String?,
    val city: String,
    val activities: List<StopResponse>,
)

data class StopResponse(
    val activityId: String,
    val title: String,
    val category: String,
    val date: String?,
    val participants: Int,
    val pricePerPerson: BigDecimal,
    val totalPrice: BigDecimal,
    val currency: String,
    val durationMinutes: Int,
    val kidFriendly: Boolean,
    val skipTheLine: Boolean,
    val rainFriendly: Boolean,
    val rationale: String?,
    val reassurance: String,
)

data class CrossSellResponse(
    val activityId: String,
    val title: String,
    val city: String,
    val category: String,
    val pricePerPerson: BigDecimal,
    val currency: String,
    val kidFriendly: Boolean,
    val skipTheLine: Boolean,
    val rainFriendly: Boolean,
    val durationMinutes: Int,
)

data class CartResponse(
    val sessionId: String,
    val items: List<CartItemResponse>,
    val total: BigDecimal,
    val currency: String,
) {
    companion object {
        fun from(snapshot: CartSnapshot): CartResponse = CartResponse(
            sessionId = snapshot.sessionId,
            items = snapshot.items.map { line ->
                CartItemResponse(
                    activityId = line.activityId,
                    title = line.title,
                    city = line.city,
                    date = line.date,
                    participants = line.participants,
                    pricePerPerson = line.pricePerPerson,
                    total = line.total,
                    currency = line.currency,
                )
            },
            total = snapshot.total,
            currency = snapshot.currency,
        )
    }
}

data class CartItemResponse(
    val activityId: String,
    val title: String,
    val city: String,
    val date: String,
    val participants: Int,
    val pricePerPerson: BigDecimal,
    val total: BigDecimal,
    val currency: String,
)
