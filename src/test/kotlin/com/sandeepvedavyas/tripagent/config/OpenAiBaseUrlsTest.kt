package com.sandeepvedavyas.tripagent.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OpenAiBaseUrlsTest {
    @Test
    fun `strips one trailing v1 and a trailing slash`() {
        assertThat(OpenAiBaseUrls.normalize("https://api.openai.com/v1")).isEqualTo("https://api.openai.com")
        assertThat(OpenAiBaseUrls.normalize("http://localhost:8081/v1/")).isEqualTo("http://localhost:8081")
        assertThat(OpenAiBaseUrls.normalize("http://127.0.0.1:9")).isEqualTo("http://127.0.0.1:9")
    }

    @Test
    fun `display drops userinfo`() {
        assertThat(OpenAiBaseUrls.display("https://user:secret@api.openai.com/v1"))
            .isEqualTo("https://api.openai.com")
    }

    @Test
    fun `rejects a non http origin`() {
        assertThatThrownBy { OpenAiBaseUrls.normalize("api.openai.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
