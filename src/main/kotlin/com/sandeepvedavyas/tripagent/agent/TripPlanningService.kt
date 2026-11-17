package com.sandeepvedavyas.tripagent.agent

import com.sandeepvedavyas.tripagent.api.CartResponse
import com.sandeepvedavyas.tripagent.api.CrossSellResponse
import com.sandeepvedavyas.tripagent.api.DayResponse
import com.sandeepvedavyas.tripagent.api.PlanRequest
import com.sandeepvedavyas.tripagent.api.PlanResponse
import com.sandeepvedavyas.tripagent.api.StopResponse
import com.sandeepvedavyas.tripagent.catalog.ActivityCatalog
import com.sandeepvedavyas.tripagent.catalog.TripCart
import com.sandeepvedavyas.tripagent.domain.DemoNotice
import com.sandeepvedavyas.tripagent.domain.Ids
import com.sandeepvedavyas.tripagent.domain.Rejection
import com.sandeepvedavyas.tripagent.domain.RequestRejectedException
import com.sandeepvedavyas.tripagent.domain.UpstreamModelException
import com.sandeepvedavyas.tripagent.tools.ActivityTools
import io.micrometer.core.instrument.MeterRegistry
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import java.net.SocketTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.stereotype.Service

@Service
class TripPlanningService(
    private val chatClient: ChatClient,
    private val tools: ActivityTools,
    private val catalog: ActivityCatalog,
    private val cart: TripCart,
    private val assembler: ItineraryAssembler,
    private val parser: ModelPlanParser,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry,
) {
    fun plan(request: PlanRequest, mode: AgentMode): PlanResponse {
        val sessionId = request.sessionId?.trim()?.ifBlank { null } ?: UUID.randomUUID().toString()
        val conversationId = request.conversationId?.trim()?.ifBlank { null } ?: sessionId
        requireSession(sessionId, "sessionId")
        requireSession(conversationId, "conversationId")
        val participants = request.participants ?: ParticipantHint.infer(request.message) ?: DEFAULT_PARTICIPANTS
        val audit = Collections.synchronizedList(mutableListOf<String>())
        val response = try {
            chatClient.prompt()
                .system(AgentPrompts.SYSTEM)
                .user(AgentPrompts.user(sessionId, participants, LocalDate.now(clock), request.message.trim(), mode))
                .tools(tools)
                .toolContext(mapOf(ActivityTools.AUDIT_KEY to audit))
                .advisors { spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId) }
                .call()
                .chatResponse()
        } catch (ex: Exception) {
            meterRegistry.counter("trip.plan.requests", "mode", mode.name.lowercase(), "outcome", "error").increment()
            log.warn(
                "model call failed mode={} type={} cause={}",
                mode.name.lowercase(),
                ex.javaClass.simpleName,
                rootCause(ex).javaClass.simpleName,
            )
            throw UpstreamModelException(
                if (isTimeout(ex)) "The model timed out." else "The model call failed.",
                isTimeout(ex),
                ex,
            )
        }
        try {
            val text = response?.result?.output?.text.orEmpty()
            val model = response?.metadata?.model?.takeIf { it.isNotBlank() }
            val assembled = assembler.assemble(parser.parse(text), text, participants)
            val cartSnapshot = if (assembled.cartLines.isNotEmpty()) {
                cart.replace(sessionId, assembled.cartLines)
            } else {
                cart.get(sessionId)
            }
            meterRegistry.counter("trip.plan.requests", "mode", mode.name.lowercase(), "outcome", "success").increment()
            log.info(
                "planned mode={} sessionId={} conversationId={} days={} tools={}",
                mode.name.lowercase(),
                sessionId,
                conversationId,
                assembled.days.size,
                audit.distinct().joinToString(","),
            )
            return PlanResponse(
                sessionId = sessionId,
                conversationId = conversationId,
                answer = assembled.answer,
                model = model,
                itinerary = assembled.days.map { day ->
                    DayResponse(
                        day = day.day,
                        date = day.date?.toString(),
                        city = day.city,
                        activities = day.activities.map { stop ->
                            StopResponse(
                                activityId = stop.activity.id,
                                title = stop.activity.title,
                                category = stop.activity.category,
                                date = stop.date?.toString(),
                                participants = stop.participants,
                                pricePerPerson = stop.quote.pricePerPerson,
                                totalPrice = stop.quote.total,
                                currency = stop.quote.currency,
                                durationMinutes = stop.activity.durationMinutes,
                                kidFriendly = stop.activity.kidFriendly,
                                skipTheLine = stop.activity.skipTheLine,
                                rainFriendly = stop.activity.rainFriendly,
                                rationale = stop.rationale,
                                reassurance = stop.reassurance,
                            )
                        },
                    )
                },
                cart = CartResponse.from(cartSnapshot),
                crossSells = assembled.crossSellIds.mapNotNull { id ->
                    catalog.find(id)?.let { activity ->
                        val card = catalog.card(activity)
                        CrossSellResponse(
                            activityId = card.activityId,
                            title = card.title,
                            city = card.city,
                            category = card.category,
                            pricePerPerson = card.pricePerPerson,
                            currency = card.currency,
                            kidFriendly = card.kidFriendly,
                            skipTheLine = card.skipTheLine,
                            rainFriendly = card.rainFriendly,
                            durationMinutes = card.durationMinutes,
                        )
                    }
                },
                objectionNotes = assembled.objectionNotes,
                toolsUsed = audit.toList(),
                warnings = assembled.warnings,
                disclaimer = DemoNotice.TEXT,
            )
        } catch (ex: RequestRejectedException) {
            throw ex
        } catch (ex: UpstreamModelException) {
            throw ex
        } catch (ex: Exception) {
            meterRegistry.counter("trip.plan.requests", "mode", mode.name.lowercase(), "outcome", "error").increment()
            log.error("plan assembly failed mode={} type={}", mode.name.lowercase(), ex.javaClass.name, ex)
            throw ex
        }
    }

    fun cart(sessionId: String): CartResponse {
        requireSession(sessionId, "sessionId")
        return CartResponse.from(cart.get(sessionId))
    }

    private fun requireSession(value: String, field: String) {
        if (!Ids.isSession(value)) {
            throw RequestRejectedException(Rejection.BAD_REQUEST, "$field must match ${Ids.SESSION.pattern}")
        }
    }

    private fun isTimeout(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { cause ->
            cause is SocketTimeoutException || cause is HttpTimeoutException
        }

    private fun rootCause(error: Throwable): Throwable =
        generateSequence(error) { it.cause }.last()

    companion object {
        const val DEFAULT_PARTICIPANTS: Int = 2
        private val log = LoggerFactory.getLogger(TripPlanningService::class.java)
    }
}
