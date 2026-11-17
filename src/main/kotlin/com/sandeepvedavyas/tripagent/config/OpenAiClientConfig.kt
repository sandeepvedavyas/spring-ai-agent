package com.sandeepvedavyas.tripagent.config

import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class OpenAiClientConfig(
    private val environment: Environment,
    private val properties: AgentProperties,
) {
    @Bean
    fun openAiApi(
        responseErrorHandler: ResponseErrorHandler,
        webClientBuilder: ObjectProvider<WebClient.Builder>,
    ): OpenAiApi {
        val baseUrl = OpenAiBaseUrls.normalize(
            environment.getProperty("spring.ai.openai.base-url")
                ?: error("spring.ai.openai.base-url is required"),
        )
        val apiKey = environment.getProperty("spring.ai.openai.api-key").orEmpty()
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY is blank. Set a real key or the local-demo placeholder." }
        val completionsPath = environment.getProperty(
            "spring.ai.openai.chat.completions-path",
            "/v1/chat/completions",
        )
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient).apply {
            setReadTimeout(properties.llm.timeout)
        }
        return OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .completionsPath(completionsPath)
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .webClientBuilder(webClientBuilder.getIfAvailable { WebClient.builder() })
            .responseErrorHandler(responseErrorHandler)
            .build()
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun chatMemory(): ChatMemory = MessageWindowChatMemory.builder().maxMessages(40).build()

    @Bean
    fun chatClient(builder: ChatClient.Builder, chatMemory: ChatMemory): ChatClient =
        builder
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build()

    @Bean
    fun startupChecks(): ApplicationRunner = ApplicationRunner { _: ApplicationArguments ->
        val baseUrl = environment.getProperty("spring.ai.openai.base-url").orEmpty()
        val model = environment.getProperty("spring.ai.openai.chat.options.model").orEmpty()
        val keyPresent = !environment.getProperty("spring.ai.openai.api-key").isNullOrBlank()
        if (properties.auth.apiKey.isBlank()) {
            log.warn("AGENT_API_KEY is blank. Every protected request will be rejected.")
        } else if (properties.auth.apiKey == AgentProperties.LOCAL_DEV_API_KEY) {
            log.warn("AGENT_API_KEY is the local-dev default. Set a unique key before exposing this service.")
        }
        log.info(
            "openai baseUrl={} model={} upstreamKeyPresent={}",
            OpenAiBaseUrls.display(baseUrl),
            model,
            keyPresent,
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(OpenAiClientConfig::class.java)
    }
}
