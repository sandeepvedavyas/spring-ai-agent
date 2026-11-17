package com.sandeepvedavyas.tripagent.agent

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModelPlan(
    val answer: String = "",
    val participants: Int? = null,
    val itinerary: List<ModelDay> = emptyList(),
    val crossSellIds: List<String> = emptyList(),
    val objectionNotes: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModelDay(
    val day: Int = 1,
    val date: String? = null,
    val activities: List<ModelActivity> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModelActivity(
    val activityId: String = "",
    val rationale: String? = null,
    val date: String? = null,
    val participants: Int? = null,
)

@Component
class ModelPlanParser(
    private val objectMapper: ObjectMapper,
) {
    fun parse(content: String): ModelPlan? {
        val json = extract(content) ?: return null
        return try {
            objectMapper.readValue(json, ModelPlan::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun extract(content: String): String? {
        val fenced = FENCE.find(content)?.groupValues?.get(1)?.trim()
        val candidate = if (!fenced.isNullOrEmpty()) fenced else content.trim()
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return candidate.substring(start, end + 1)
    }

    companion object {
        private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    }
}
