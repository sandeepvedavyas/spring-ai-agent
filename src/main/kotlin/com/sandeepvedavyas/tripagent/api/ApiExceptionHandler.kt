package com.sandeepvedavyas.tripagent.api

import com.sandeepvedavyas.tripagent.auth.RequestIdFilter
import com.sandeepvedavyas.tripagent.domain.Rejection
import com.sandeepvedavyas.tripagent.domain.RequestRejectedException
import com.sandeepvedavyas.tripagent.domain.UpstreamModelException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {
    @ExceptionHandler(RequestRejectedException::class)
    fun rejected(ex: RequestRejectedException): ProblemDetail {
        val status = when (ex.rejection) {
            Rejection.BAD_REQUEST -> HttpStatus.BAD_REQUEST
        }
        return problem(status, ex.message ?: "Request rejected")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(ex: MethodArgumentNotValidException): ProblemDetail {
        val detail = ex.bindingResult.fieldErrors
            .joinToString("; ") { error -> "${error.field} ${error.defaultMessage}" }
            .ifBlank { "Request validation failed" }
        return problem(HttpStatus.BAD_REQUEST, detail)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(@Suppress("UNUSED_PARAMETER") ex: HttpMessageNotReadableException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Request body is not valid JSON")

    @ExceptionHandler(UpstreamModelException::class)
    fun upstream(ex: UpstreamModelException): ProblemDetail {
        val status = if (ex.timeout) HttpStatus.GATEWAY_TIMEOUT else HttpStatus.BAD_GATEWAY
        return problem(status, ex.message ?: "The model call failed.")
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception): ProblemDetail {
        log.error("unhandled type={}", ex.javaClass.name, ex)
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed.")
    }

    private fun problem(status: HttpStatus, detail: String): ProblemDetail {
        val body = ProblemDetail.forStatusAndDetail(status, detail)
        MDC.get(RequestIdFilter.MDC_KEY)?.let { body.setProperty("requestId", it) }
        return body
    }

    private companion object {
        val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
    }
}
