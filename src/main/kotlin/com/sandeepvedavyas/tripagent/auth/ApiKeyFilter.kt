package com.sandeepvedavyas.tripagent.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.sandeepvedavyas.tripagent.config.AgentProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class ApiKeyFilter(
    properties: AgentProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val verifier = ApiKeyVerifier(properties.auth.apiKey)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI
        if (isPublic(path) || verifier.matches(request.getHeader(ApiKeyVerifier.HEADER))) {
            filterChain.doFilter(request, response)
            return
        }
        log.warn("rejected request method={} path={} reason=unauthorized", request.method, path)
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Missing or invalid API key")
        MDC.get(RequestIdFilter.MDC_KEY)?.let { problem.setProperty("requestId", it) }
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.setHeader("WWW-Authenticate", "ApiKey realm=\"spring-ai-agent\"")
        objectMapper.writeValue(response.outputStream, problem)
    }

    private fun isPublic(path: String): Boolean = when {
        path == "/" -> true
        path == "/actuator/health" || path.startsWith("/actuator/health/") -> true
        path == "/actuator/info" -> true
        else -> false
    }

    private companion object {
        val log = LoggerFactory.getLogger(ApiKeyFilter::class.java)
    }
}
