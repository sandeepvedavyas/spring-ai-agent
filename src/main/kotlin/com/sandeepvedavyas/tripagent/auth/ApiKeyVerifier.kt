package com.sandeepvedavyas.tripagent.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Single shared API key, compared by SHA-256 digest so a length mismatch does
 * not return early. This is a demo service credential, not a user directory.
 */
class ApiKeyVerifier(
    expected: String,
) {
    private val expectedDigest: ByteArray? = expected.takeIf { it.isNotBlank() }?.let(::digest)

    fun matches(presented: String?): Boolean {
        val expected = expectedDigest ?: return false
        val token = presented?.trim()?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) } ?: return false
        return MessageDigest.isEqual(digest(token), expected)
    }

    companion object {
        const val HEADER: String = "X-API-Key"

        private fun digest(value: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
    }
}
