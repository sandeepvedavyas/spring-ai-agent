package com.sandeepvedavyas.tripagent.agent

import com.sandeepvedavyas.tripagent.catalog.Activity
import com.sandeepvedavyas.tripagent.catalog.ActivityCatalog
import com.sandeepvedavyas.tripagent.catalog.Availability
import com.sandeepvedavyas.tripagent.catalog.BadCatalogInput
import com.sandeepvedavyas.tripagent.catalog.CartLine
import com.sandeepvedavyas.tripagent.catalog.Pricing
import java.time.LocalDate
import org.springframework.stereotype.Component

data class AssembledPlan(
    val answer: String,
    val days: List<AssembledDay>,
    val cartLines: List<CartLine>,
    val crossSellIds: List<String>,
    val objectionNotes: List<String>,
    val warnings: List<String>,
)

data class AssembledDay(
    val day: Int,
    val date: LocalDate?,
    val city: String,
    val activities: List<AssembledActivity>,
)

data class AssembledActivity(
    val activity: Activity,
    val date: LocalDate?,
    val participants: Int,
    val rationale: String?,
    val quote: com.sandeepvedavyas.tripagent.catalog.PriceQuote,
    val reassurance: String,
)

@Component
class ItineraryAssembler(
    private val catalog: ActivityCatalog,
) {
    fun assemble(draft: ModelPlan?, fallbackAnswer: String, participantsHint: Int): AssembledPlan {
        val warnings = mutableListOf<String>()
        if (draft == null) {
            return AssembledPlan(
                answer = clip(fallbackAnswer.ifBlank { EMPTY_ANSWER }),
                days = emptyList(),
                cartLines = emptyList(),
                crossSellIds = emptyList(),
                objectionNotes = emptyList(),
                warnings = warnings,
            )
        }
        val bookedIds = mutableSetOf<String>()
        val days = draft.itinerary.mapNotNull { day ->
            val date = parseDate(day.date, warnings)
            val stops = day.activities.mapNotNull { stop ->
                val activity = catalog.find(stop.activityId)
                if (activity == null) {
                    if (stop.activityId.isNotBlank()) {
                        warnings += "Ignored unknown activity ${stop.activityId}."
                    }
                    return@mapNotNull null
                }
                val stopDate = parseDate(stop.date, warnings) ?: date
                val requested = stop.participants ?: draft.participants ?: participantsHint
                val participants = requested.coerceIn(activity.minParticipants, activity.maxParticipants)
                if (participants != requested) {
                    warnings += "Adjusted ${activity.id} from $requested to $participants participants."
                }
                if (stopDate != null && Availability.remaining(activity, stopDate) < participants) {
                    warnings += "${activity.id} is sold out on $stopDate."
                    return@mapNotNull null
                }
                bookedIds += activity.id
                val summary = catalog.summary(activity)
                AssembledActivity(
                    activity = activity,
                    date = stopDate,
                    participants = participants,
                    rationale = stop.rationale?.trim()?.ifBlank { null },
                    quote = Pricing.quote(activity, participants),
                    reassurance = listOf(summary.skipTheLine, summary.kidFriendly, summary.rain).joinToString(" "),
                )
            }
            if (stops.isEmpty()) {
                null
            } else {
                AssembledDay(
                    day = day.day,
                    date = date ?: stops.firstNotNullOfOrNull { it.date },
                    city = stops.first().activity.city,
                    activities = stops,
                )
            }
        }.sortedBy { it.day }

        val crossSells = LinkedHashSet<String>()
        draft.crossSellIds.forEach { id ->
            if (catalog.find(id) == null) {
                warnings += "Ignored unknown cross-sell $id."
            } else if (id !in bookedIds) {
                crossSells += id
            }
        }
        days.firstOrNull()?.activities?.firstOrNull()?.activity?.let { hero ->
            catalog.crossSell(hero.id).suggestions.forEach { suggestion ->
                if (suggestion.activityId !in bookedIds) {
                    crossSells += suggestion.activityId
                }
            }
        }

        val lines = days.flatMap { day ->
            day.activities.mapNotNull { stop ->
                val date = stop.date ?: return@mapNotNull null
                CartLine(
                    activityId = stop.activity.id,
                    title = stop.activity.title,
                    city = stop.activity.city,
                    date = date.toString(),
                    participants = stop.participants,
                    pricePerPerson = stop.quote.pricePerPerson,
                    total = stop.quote.total,
                    currency = stop.quote.currency,
                )
            }
        }
        val answer = clip(draft.answer.ifBlank { fallbackAnswer }.ifBlank { EMPTY_ANSWER })
        return AssembledPlan(
            answer = answer,
            days = days,
            cartLines = lines,
            crossSellIds = crossSells.take(3).toList(),
            objectionNotes = draft.objectionNotes.map { it.trim() }.filter { it.isNotEmpty() }.take(6),
            warnings = warnings,
        )
    }

    private fun parseDate(raw: String?, warnings: MutableList<String>): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            catalog.parseRequiredDate(raw)
        } catch (_: BadCatalogInput) {
            warnings += "Ignored date '$raw' because it is not yyyy-MM-dd."
            null
        }
    }

    private fun clip(text: String): String = text.trim().take(MAX_ANSWER)

    companion object {
        const val EMPTY_ANSWER: String = "I could not build an itinerary from the catalog."
        const val MAX_ANSWER: Int = 4000
    }
}
