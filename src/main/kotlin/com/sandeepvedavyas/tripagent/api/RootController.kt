package com.sandeepvedavyas.tripagent.api

import com.sandeepvedavyas.tripagent.domain.DemoNotice
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class RootController {
    @GetMapping("/")
    fun root(): Map<String, String> = mapOf(
        "service" to "spring-ai-agent",
        "disclaimer" to DemoNotice.TEXT,
        "health" to "/actuator/health",
        "plan" to "/api/v1/plan",
        "chat" to "/api/v1/chat",
        "cart" to "/api/v1/sessions/{sessionId}/cart",
    )
}
