package com.ongo.infrastructure.render

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class OpenAiTextToSpeechAdapterHttpContractTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `TTS는 OpenAI 호환 endpoint에 인증과 음성 요청을 전송한다`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("mp3-bytes"))
        val client = OpenAiTextToSpeechAdapter(
            apiKey = "live-openai-key",
            baseUrl = server.url("/v1").toString().removeSuffix("/"),
            model = "test-tts-model",
        )

        val audio = client.synthesize("테스트 자막", "alloy")
        try {
            val request = server.takeRequest()
            assertThat(request.path).isEqualTo("/v1/audio/speech")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer live-openai-key")
            assertThat(request.getHeader("Content-Type")).startsWith("application/json")
            assertThat(request.body.readUtf8())
                .contains("\"model\":\"test-tts-model\"")
                .contains("\"voice\":\"alloy\"")
                .contains("테스트 자막")
                .contains("\"response_format\":\"mp3\"")
            assertThat(Files.readAllBytes(audio.path)).containsExactly(*"mp3-bytes".toByteArray())
        } finally {
            Files.deleteIfExists(audio.path)
        }
    }

    @Test
    fun `가짜 API key에서는 음성 목록을 노출하지 않는다`() {
        val client = OpenAiTextToSpeechAdapter(
            apiKey = "dummy-openai-key",
            baseUrl = server.url("/v1").toString().removeSuffix("/"),
            model = "test-tts-model",
        )

        assertThat(client.availableVoices()).isEmpty()
    }
}
