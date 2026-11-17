package com.sandeepvedavyas.tripagent.api

import com.sandeepvedavyas.tripagent.agent.AgentMode
import com.sandeepvedavyas.tripagent.agent.TripPlanningService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PlanController(
    private val trips: TripPlanningService,
) {
    @PostMapping("/api/v1/plan")
    fun plan(@Valid @RequestBody request: PlanRequest): PlanResponse = trips.plan(request, AgentMode.PLAN)

    @PostMapping("/api/v1/chat")
    fun chat(@Valid @RequestBody request: PlanRequest): PlanResponse = trips.plan(request, AgentMode.CHAT)

    @GetMapping("/api/v1/sessions/{sessionId}/cart")
    fun cart(@PathVariable sessionId: String): CartResponse = trips.cart(sessionId)
}
