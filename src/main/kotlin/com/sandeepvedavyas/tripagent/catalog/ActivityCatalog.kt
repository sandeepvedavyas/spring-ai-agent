package com.sandeepvedavyas.tripagent.catalog

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandeepvedavyas.tripagent.domain.DemoNotice
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.springframework.stereotype.Component

@Component
class ActivityCatalog(
    objectMapper: ObjectMapper,
) {
    private val document: CatalogDocument = load(objectMapper)
    val disclaimer: String = document.disclaimer
    val activities: List<Activity> = document.activities
    private val byId: Map<String, Activity> = activities.associateBy { it.id }

    init {
        validate()
    }

    val size: Int get() = activities.size

    val cities: Set<String> get() = activities.map { it.city }.toSet()

    fun find(activityId: String): Activity? = byId[activityId.trim()]

    fun search(city: String, category: String, maxPrice: Double, date: String): SearchResult {
        val resolved = resolveCity(city)
            ?: return SearchResult(
                city = null,
                date = date.ifBlank { null },
                activities = emptyList(),
                error = "Unknown city. Known cities: ${cities.sorted().joinToString(", ")}.",
            )
        val day = parseOptionalDate(date)
        val limit = maxPrice.takeIf { it > 0.0 }?.let { BigDecimal.valueOf(it) }
        val matched = activities.filter { activity ->
            activity.city.equals(resolved, ignoreCase = true) &&
                matchesCategory(activity, category) &&
                (limit == null || activity.pricePerPerson <= limit) &&
                (day == null || Availability.remaining(activity, day) > 0)
        }
        val hidden = if (day == null) {
            0
        } else {
            activities.count { activity ->
                activity.city.equals(resolved, ignoreCase = true) &&
                    matchesCategory(activity, category) &&
                    (limit == null || activity.pricePerPerson <= limit) &&
                    Availability.remaining(activity, day) <= 0
            }
        }
        val cards = matched
            .sortedWith(compareByDescending<Activity> { it.averageRating() }.thenBy { it.pricePerPerson })
            .take(MAX_RESULTS)
            .map(::card)
        val note = if (hidden > 0) {
            "$hidden matching activities are sold out on $day and were left out."
        } else {
            null
        }
        return SearchResult(city = resolved, date = day?.toString(), activities = cards, note = note)
    }

    fun price(activityId: String, participants: Int): PriceQuote {
        val activity = find(activityId)
            ?: return PriceQuote(
                activityId = activityId,
                title = "",
                participants = participants,
                pricePerPerson = BigDecimal.ZERO.setScale(2),
                total = BigDecimal.ZERO.setScale(2),
                currency = "EUR",
                groupDiscountApplied = false,
                error = "Unknown activityId $activityId.",
            )
        Pricing.participantError(activity, participants)?.let { message ->
            return PriceQuote(
                activityId = activity.id,
                title = activity.title,
                participants = participants,
                pricePerPerson = activity.pricePerPerson.setScale(2, RoundingMode.HALF_UP),
                total = BigDecimal.ZERO.setScale(2),
                currency = activity.currency,
                groupDiscountApplied = false,
                error = message,
            )
        }
        return Pricing.quote(activity, participants)
    }

    fun availability(activityId: String, date: String, participants: Int): AvailabilityQuote {
        val activity = find(activityId)
            ?: return AvailabilityQuote(
                activityId = activityId,
                date = date,
                participants = participants,
                available = false,
                remaining = 0,
                reason = "Unknown activity.",
                error = "Unknown activityId $activityId.",
            )
        val day = parseRequiredDate(date)
        Pricing.participantError(activity, participants)?.let { message ->
            return AvailabilityQuote(
                activityId = activity.id,
                date = day.toString(),
                participants = participants,
                available = false,
                remaining = Availability.remaining(activity, day),
                reason = message,
                error = message,
            )
        }
        return Availability.quote(activity, day, participants)
    }

    fun summarizeReviews(activityId: String): ReviewSummary {
        val activity = find(activityId)
            ?: return ReviewSummary(
                activityId = activityId,
                title = "",
                averageRating = 0.0,
                reviewCount = 0,
                skipTheLine = "",
                kidFriendly = "",
                rain = "",
                excerpts = emptyList(),
                error = "Unknown activityId $activityId.",
            )
        return summary(activity)
    }

    fun summary(activity: Activity): ReviewSummary = ReviewSummary(
        activityId = activity.id,
        title = activity.title,
        averageRating = activity.averageRating(),
        reviewCount = activity.reviews.size,
        skipTheLine = if (activity.skipTheLine) {
            "Skip-the-line: included. Travelers do not join the general entry queue."
        } else {
            "Skip-the-line: not included. Expect the regular entry line."
        },
        kidFriendly = if (activity.kidFriendly) {
            "Kid friendly: yes. Families are a normal part of this activity."
        } else {
            "Kid friendly: no. This one is a poor fit for young children."
        },
        rain = if (activity.rainFriendly) {
            "Rain: covered or indoors, so a wet forecast does not cancel the plan."
        } else {
            "Rain: outdoors. Book a museum or food stop as the wet-weather backup."
        },
        excerpts = activity.reviews.take(2).map { it.text },
    )

    fun crossSell(bookedActivityId: String): CrossSellResult {
        val activity = find(bookedActivityId)
            ?: return CrossSellResult(
                bookedActivityId = bookedActivityId,
                suggestions = emptyList(),
                error = "Unknown activityId $bookedActivityId.",
            )
        val explicit = activity.crossSellIds.mapNotNull(::find)
        val fallback = activities.filter { candidate ->
            candidate.city == activity.city &&
                candidate.id != activity.id &&
                candidate.category != activity.category &&
                (!activity.kidFriendly || candidate.kidFriendly)
        }
        val suggestions = (explicit + fallback)
            .distinctBy { it.id }
            .take(MAX_CROSS_SELL)
            .map(::card)
        return CrossSellResult(bookedActivityId = activity.id, suggestions = suggestions)
    }

    fun card(activity: Activity): ActivityCard = ActivityCard(
        activityId = activity.id,
        title = activity.title,
        city = activity.city,
        category = activity.category,
        summary = activity.summary,
        pricePerPerson = activity.pricePerPerson.setScale(2, RoundingMode.HALF_UP),
        currency = activity.currency,
        durationMinutes = activity.durationMinutes,
        kidFriendly = activity.kidFriendly,
        skipTheLine = activity.skipTheLine,
        rainFriendly = activity.rainFriendly,
        averageRating = activity.averageRating(),
    )

    fun resolveCity(raw: String): String? {
        val key = raw.trim().lowercase().substringBefore(",").trim()
        return CITY_ALIASES[key]
    }

    fun parseOptionalDate(raw: String): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals("any", ignoreCase = true) || trimmed.equals("null", ignoreCase = true)) {
            return null
        }
        return parseRequiredDate(trimmed)
    }

    fun parseRequiredDate(raw: String): LocalDate = try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        throw BadCatalogInput("date must be yyyy-MM-dd")
    }

    private fun matchesCategory(activity: Activity, category: String): Boolean {
        val value = category.trim().lowercase()
        if (value.isEmpty() || value == "any" || value == "all") return true
        if (value in FAMILY_WORDS) {
            return activity.kidFriendly ||
                activity.category.equals("family", ignoreCase = true) ||
                activity.tags.any { it.equals("family", ignoreCase = true) }
        }
        return activity.category.equals(value, ignoreCase = true) ||
            activity.tags.any { it.equals(value, ignoreCase = true) }
    }

    private fun validate() {
        require(disclaimer.contains("GetYourGuide")) { "Catalog disclaimer must say this is not GetYourGuide." }
        require(disclaimer.contains("not scraped") || disclaimer.contains("were not scraped")) {
            "Catalog disclaimer must say the data was not scraped."
        }
        require(activities.size >= 30) { "Demo catalog must contain at least 30 activities." }
        require(byId.size == activities.size) { "Activity ids must be unique." }
        require(cities.containsAll(REQUIRED_CITIES)) { "Catalog must cover Lisbon, Barcelona, Berlin, and Rome." }
        activities.forEach { activity ->
            require(activity.pricePerPerson > BigDecimal.ZERO) { "${activity.id} needs a positive price." }
            require(activity.currency == "EUR") { "${activity.id} must be priced in EUR." }
            require(activity.minParticipants >= 1) { "${activity.id} minParticipants must be >= 1." }
            require(activity.maxParticipants >= activity.minParticipants) {
                "${activity.id} maxParticipants must be >= minParticipants."
            }
            require(activity.reviews.isNotEmpty()) { "${activity.id} needs at least one review." }
            require(activity.reviews.all { it.rating in 1..5 }) { "${activity.id} has a rating outside 1..5." }
            activity.crossSellIds.forEach { crossId ->
                require(crossId in byId) { "${activity.id} cross-sells unknown id $crossId." }
            }
        }
        require(DemoNotice.TEXT.contains("GetYourGuide"))
    }

    companion object {
        const val RESOURCE: String = "/catalog/activities.json"

        private fun load(objectMapper: ObjectMapper): CatalogDocument {
            val stream = ActivityCatalog::class.java.getResourceAsStream(RESOURCE)
                ?: error("Missing $RESOURCE")
            return stream.use { objectMapper.readValue(it, CatalogDocument::class.java) }
        }
        const val MAX_RESULTS: Int = 8
        const val MAX_CROSS_SELL: Int = 3
        val REQUIRED_CITIES: Set<String> = setOf("Lisbon", "Barcelona", "Berlin", "Rome")
        val FAMILY_WORDS: Set<String> = setOf("family", "kids", "kid", "children", "child")
        val CITY_ALIASES: Map<String, String> = mapOf(
            "lisbon" to "Lisbon",
            "lisboa" to "Lisbon",
            "barcelona" to "Barcelona",
            "barca" to "Barcelona",
            "berlin" to "Berlin",
            "rome" to "Rome",
            "roma" to "Rome",
        )
    }
}

class BadCatalogInput(message: String) : IllegalArgumentException(message)
