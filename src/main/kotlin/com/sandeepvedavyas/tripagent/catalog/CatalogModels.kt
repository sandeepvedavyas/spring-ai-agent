package com.sandeepvedavyas.tripagent.catalog

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal

@JsonIgnoreProperties(ignoreUnknown = true)
data class CatalogDocument(
    val disclaimer: String,
    val activities: List<Activity>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Activity(
    val id: String,
    val city: String,
    val title: String,
    val category: String,
    val summary: String,
    val pricePerPerson: BigDecimal,
    val currency: String,
    val durationMinutes: Int,
    val kidFriendly: Boolean,
    val skipTheLine: Boolean,
    val rainFriendly: Boolean,
    val minParticipants: Int,
    val maxParticipants: Int,
    val tags: List<String> = emptyList(),
    val crossSellIds: List<String> = emptyList(),
    val defaultCapacity: Int = 20,
    val closedDates: List<String> = emptyList(),
    val dateCapacity: Map<String, Int> = emptyMap(),
    val reviews: List<Review> = emptyList(),
) {
    fun averageRating(): Double {
        if (reviews.isEmpty()) return 0.0
        val average = reviews.sumOf { it.rating }.toDouble() / reviews.size
        return kotlin.math.round(average * 10.0) / 10.0
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Review(
    val rating: Int,
    val author: String,
    val text: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ActivityCard(
    val activityId: String,
    val title: String,
    val city: String,
    val category: String,
    val summary: String,
    val pricePerPerson: BigDecimal,
    val currency: String,
    val durationMinutes: Int,
    val kidFriendly: Boolean,
    val skipTheLine: Boolean,
    val rainFriendly: Boolean,
    val averageRating: Double,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SearchResult(
    val city: String?,
    val date: String?,
    val activities: List<ActivityCard>,
    val note: String? = null,
    val error: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PriceQuote(
    val activityId: String,
    val title: String,
    val participants: Int,
    val pricePerPerson: BigDecimal,
    val total: BigDecimal,
    val currency: String,
    val groupDiscountApplied: Boolean,
    val error: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AvailabilityQuote(
    val activityId: String,
    val date: String,
    val participants: Int,
    val available: Boolean,
    val remaining: Int,
    val reason: String,
    val error: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReviewSummary(
    val activityId: String,
    val title: String,
    val averageRating: Double,
    val reviewCount: Int,
    val skipTheLine: String,
    val kidFriendly: String,
    val rain: String,
    val excerpts: List<String>,
    val error: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CrossSellResult(
    val bookedActivityId: String,
    val suggestions: List<ActivityCard>,
    val error: String? = null,
)

data class ToolError(
    val error: String,
)

data class CartLine(
    val activityId: String,
    val title: String,
    val city: String,
    val date: String,
    val participants: Int,
    val pricePerPerson: BigDecimal,
    val total: BigDecimal,
    val currency: String,
)

data class CartSnapshot(
    val sessionId: String,
    val items: List<CartLine>,
    val total: BigDecimal,
    val currency: String,
)
