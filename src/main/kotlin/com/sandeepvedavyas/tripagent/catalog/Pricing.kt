package com.sandeepvedavyas.tripagent.catalog

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

object Pricing {
    private val GROUP_RATE: BigDecimal = BigDecimal("0.90")
    const val GROUP_THRESHOLD: Int = 4

    fun quote(activity: Activity, participants: Int): PriceQuote {
        val unit = activity.pricePerPerson.setScale(2, RoundingMode.HALF_UP)
        val discounted = if (participants >= GROUP_THRESHOLD) {
            unit.multiply(GROUP_RATE).setScale(2, RoundingMode.HALF_UP)
        } else {
            unit
        }
        val total = discounted.multiply(BigDecimal(participants)).setScale(2, RoundingMode.HALF_UP)
        return PriceQuote(
            activityId = activity.id,
            title = activity.title,
            participants = participants,
            pricePerPerson = discounted,
            total = total,
            currency = activity.currency,
            groupDiscountApplied = participants >= GROUP_THRESHOLD,
        )
    }

    fun participantError(activity: Activity, participants: Int): String? = when {
        participants < activity.minParticipants ->
            "participants must be at least ${activity.minParticipants} for ${activity.id}"
        participants > activity.maxParticipants ->
            "participants must be at most ${activity.maxParticipants} for ${activity.id}"
        else -> null
    }
}

object Availability {
    fun quote(activity: Activity, date: LocalDate, participants: Int): AvailabilityQuote {
        val remaining = remaining(activity, date)
        val available = remaining >= participants && remaining > 0
        val reason = when {
            remaining <= 0 -> "Sold out on $date."
            remaining < participants -> "Only $remaining places left on $date."
            else -> "Available for $participants on $date."
        }
        return AvailabilityQuote(
            activityId = activity.id,
            date = date.toString(),
            participants = participants,
            available = available,
            remaining = remaining,
            reason = reason,
        )
    }

    fun remaining(activity: Activity, date: LocalDate?): Int {
        if (date == null) return activity.defaultCapacity
        if (date.toString() in activity.closedDates) return 0
        return activity.dateCapacity[date.toString()] ?: activity.defaultCapacity
    }
}
