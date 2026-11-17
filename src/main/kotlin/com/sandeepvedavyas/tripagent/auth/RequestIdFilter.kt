package com.sandeepvedavyas.tripagent.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)
            ?.trim()
            ?.takeIf { it.length in 1..128 && it.all { char -> char.isLetterOrDigit() || char in "-_." } }
            ?: UUID.randomUUID().toString()
        response.setHeader(HEADER, requestId)
        MDC.put(MDC_KEY, requestId)
        val started = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val path = request.requestURI
            if (path.startsWith("/api/")) {
                log.info(
                    "request method={} path={} status={} requestId={} elapsedMs={}",
                    request.method,
                    path,
                    response.status,
                    requestId,
                    (System.nanoTime() - started) / 1_000_000,
                )
            }
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER: String = "X-Request-Id"
        const val MDC_KEY: String = "requestId"
        private val log = LoggerFactory.getLogger(RequestIdFilter::class.java)
    }
}
