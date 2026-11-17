package com.sandeepvedavyas.tripagent.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApiKeyVerifierTest {
    private val verifier = ApiKeyVerifier("local-dev-key")

    @Test
    fun `matches only the configured key`() {
        assertThat(verifier.matches("local-dev-key")).isTrue()
        assertThat(verifier.matches(" local-dev-key ")).isTrue()
        assertThat(verifier.matches("local-dev-key-other")).isFalse()
        assertThat(verifier.matches("short")).isFalse()
        assertThat(verifier.matches(null)).isFalse()
        assertThat(verifier.matches("local dev key")).isFalse()
    }

    @Test
    fun `blank expected key fails closed`() {
        assertThat(ApiKeyVerifier("").matches("")).isFalse()
        assertThat(ApiKeyVerifier("   ").matches("local-dev-key")).isFalse()
    }
}
