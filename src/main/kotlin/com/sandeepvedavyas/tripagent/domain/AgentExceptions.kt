package com.sandeepvedavyas.tripagent.domain

class RequestRejectedException(
    val rejection: Rejection,
    message: String,
) : RuntimeException(message)

enum class Rejection {
    BAD_REQUEST,
}

class UpstreamModelException(
    message: String,
    val timeout: Boolean,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
