package com.ongo.infrastructure.render

import com.ongo.application.video.GeneratedAudioFile
import com.ongo.application.video.TextToSpeechPort
import com.ongo.application.video.TextToSpeechVoice
import com.ongo.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.util.UUID

/**
 * Optional OpenAI-compatible TTS adapter. The endpoint and model are
 * configurable so an OpenAI-compatible gateway can be used in production.
 */
@Component
class OpenAiTextToSpeechAdapter(
    @param:Value("\${spring.ai.openai.api-key:}")
    private val apiKey: String,
    @param:Value("\${video.generation.tts.base-url:https://api.openai.com/v1}")
    baseUrl: String,
    @param:Value("\${video.generation.tts.model:gpt-4o-mini-tts}")
    private val model: String,
) : TextToSpeechPort {
    private val client = RestClient.builder().baseUrl(baseUrl.trimEnd('/')).build()

    override fun availableVoices(): List<TextToSpeechVoice> =
        if (isConfigured()) VOICES else emptyList()

    override fun synthesize(text: String, voiceId: String): GeneratedAudioFile {
        require(text.isNotBlank() && text.length <= MAX_TEXT_LENGTH) {
            "음성 합성 텍스트는 1~${MAX_TEXT_LENGTH}자여야 합니다"
        }
        if (!isConfigured()) {
            throw BusinessException(
                "VIDEO_VOICE_UNAVAILABLE",
                "음성 공급자가 설정되지 않았습니다. OPENAI_API_KEY를 설정하거나 voice를 제거하세요.",
            )
        }
        if (VOICES.none { it.id == voiceId }) {
            throw IllegalArgumentException("지원하지 않는 음성입니다: $voiceId")
        }

        val bytes = client.post()
            .uri("/audio/speech")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $apiKey")
            .body(
                mapOf(
                    "model" to model,
                    "voice" to voiceId,
                    "input" to text,
                    "response_format" to "mp3",
                ),
            )
            .retrieve()
            .body(ByteArray::class.java)
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("음성 공급자가 빈 오디오를 반환했습니다")

        val path = Files.createTempFile("ongo-tts-${UUID.randomUUID()}", ".mp3")
        try {
            Files.write(path, bytes)
            return GeneratedAudioFile(path, bytes.size.toLong())
        } catch (error: Exception) {
            Files.deleteIfExists(path)
            throw error
        }
    }

    private fun isConfigured(): Boolean =
        apiKey.isNotBlank() && !apiKey.startsWith("dummy-") && !apiKey.equals("your-api-key", ignoreCase = true)

    companion object {
        private const val MAX_TEXT_LENGTH = 4_000
        private val VOICES = listOf(
            TextToSpeechVoice("alloy", "Alloy"),
            TextToSpeechVoice("ash", "Ash"),
            TextToSpeechVoice("ballad", "Ballad"),
            TextToSpeechVoice("coral", "Coral"),
            TextToSpeechVoice("echo", "Echo"),
            TextToSpeechVoice("fable", "Fable"),
            TextToSpeechVoice("onyx", "Onyx"),
            TextToSpeechVoice("nova", "Nova"),
            TextToSpeechVoice("sage", "Sage"),
            TextToSpeechVoice("shimmer", "Shimmer"),
            TextToSpeechVoice("verse", "Verse"),
            TextToSpeechVoice("marin", "Marin"),
            TextToSpeechVoice("cedar", "Cedar"),
        )
    }
}
