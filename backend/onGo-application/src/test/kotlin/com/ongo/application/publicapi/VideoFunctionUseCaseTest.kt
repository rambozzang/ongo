package com.ongo.application.publicapi

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.TextToSpeechPort
import com.ongo.application.video.TextToSpeechVoice
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class VideoFunctionUseCaseTest {
    private val tts = mockk<TextToSpeechPort>()
    private val useCase = VideoFunctionUseCase(tts, jacksonObjectMapper())

    @Test
    fun `loadVoices returns configured provider voices`() {
        every { tts.availableVoices() } returns listOf(TextToSpeechVoice("alloy", "Alloy"))

        val result = useCase.execute(PublicVideoFunctionRequest("loadVoices", "image-text-slides"))

        assertEquals("alloy", result.path("voices").single().path("id").asText())
    }

    @Test
    fun `unsupported video function is rejected`() {
        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase.execute(PublicVideoFunctionRequest("renderStoryboard", "image-text-slides"))
        }
    }
}
