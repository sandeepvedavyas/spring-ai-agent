package com.sandeepvedavyas.tripagent.config

import java.net.URI

/**
 * Spring AI appends `/v1/chat/completions` to the origin. Callers of this
 * service often paste a base that already ends in `/v1` (the gateway, Ollama,
 * or OpenAI's documented URL). Strip one trailing `/v1` so it is not doubled.
 */
object OpenAiBaseUrls {
    fun normalize(raw: String): String {
        var url = raw.trim().trimEnd('/')
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "OPENAI_BASE_URL must start with http:// or https://"
        }
        if (url.endsWith("/v1")) {
            url = url.removeSuffix("/v1").trimEnd('/')
        }
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "OPENAI_BASE_URL must start with http:// or https://"
        }
        return url
    }

    /** Origin used in logs. Userinfo is dropped so a key embedded in the URL is not printed. */
    fun display(raw: String): String = try {
        val uri = URI(normalize(raw))
        if (uri.host.isNullOrBlank()) "(invalid)"
        else URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString()
    } catch (_: Exception) {
        "(invalid)"
    }
}
