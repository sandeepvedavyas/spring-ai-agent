package com.sandeepvedavyas.tripagent.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent")
data class AgentProperties(
    val auth: Auth = Auth(),
    val llm: Llm = Llm(),
) {
    data class Auth(
        val apiKey: String = LOCAL_DEV_API_KEY,
    )

    data class Llm(
        val timeout: Duration = Duration.ofSeconds(30),
    )

    init {
        require(llm.timeout.toSeconds() in 1..120) { "agent.llm.timeout must be between 1s and 120s" }
    }

    companion object {
        const val LOCAL_DEV_API_KEY: String = "local-dev-key"
    }
}
