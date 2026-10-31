package com.ongo.infrastructure.ai

import com.openai.client.OpenAIClient
import com.openai.core.MultipartField
import com.openai.models.audio.transcriptions.TranscriptionCreateParams
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.audio.VerboseTranscriber
import io.micrometer.observation.ObservationRegistry
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions
import org.springframework.ai.openai.setup.OpenAiSetup
import org.springframework.stereotype.Component

/**
 * 전사 응답을 **시각까지** 받아 온다. 인증·주소·시간 제한은 Spring AI 가 자동 구성한 전사 모델의
 * 설정을 그대로 쓴다 — 같은 키로 같은 곳에 보내므로 설정이 두 벌이 되지 않는다.
 */
@Component
class OpenAiVerboseTranscriber(
    private val model: OpenAiAudioTranscriptionModel,
) : VerboseTranscriber {

    private val client: OpenAIClient by lazy {
        val o = model.options
        OpenAiSetup.setupSyncClient(
            o.baseUrl, o.apiKey, o.credential, o.microsoftDeploymentName, o.microsoftFoundryServiceVersion,
            o.organizationId, o.isMicrosoftFoundry, o.isGitHubModels, o.model, o.timeout, o.maxRetries,
            o.proxy, o.customHeaders, ObservationRegistry.NOOP, null, emptyList(),
        )
    }

    override fun transcribe(prompt: AudioTranscriptionPrompt): String {
        val params = buildParams(prompt)
        return toJson(client.audio().transcriptions().create(params))
    }

    companion object {
        fun buildParams(prompt: AudioTranscriptionPrompt): TranscriptionCreateParams {
            val options = prompt.options as? OpenAiAudioTranscriptionOptions
                ?: throw IllegalArgumentException("OpenAI 전사 옵션이 필요하다")
            val resource = prompt.instructions
            val bytes = resource.inputStream.use { it.readAllBytes() }
            val builder = TranscriptionCreateParams.builder()
                .file(MultipartField.builder<java.io.InputStream>().value(bytes.inputStream()).filename(resource.filename ?: "audio").build())
                .model(requireNotNull(options.model) { "전사 모델 이름이 필요하다" })
            options.responseFormat?.let { builder.responseFormat(it) }
            options.language?.let { builder.language(it) }
            options.prompt?.let { builder.prompt(it) }
            options.temperature?.let { builder.temperature(it.toDouble()) }
            options.timestampGranularities?.takeIf { it.isNotEmpty() }?.let { builder.timestampGranularities(it) }
            return builder.build()
        }

        private val mapper = jacksonObjectMapper()

        /** API 응답과 같은 모양(`text`, `segments[start,end,text]`, `words[word,start,end]`)의 JSON. */
        fun toJson(response: TranscriptionCreateResponse): String {
            val body: Map<String, Any> = when {
                response.isVerbose() -> {
                    val v = response.asVerbose()
                    mapOf(
                        "text" to v.text(),
                        "segments" to v.segments().orElse(emptyList()).map {
                            mapOf("start" to it.start(), "end" to it.end(), "text" to it.text())
                        },
                        "words" to v.words().orElse(emptyList()).map {
                            mapOf("word" to it.word(), "start" to it.start(), "end" to it.end())
                        },
                    )
                }
                response.isTranscription() -> mapOf("text" to response.asTranscription().text())
                response.isDiarized() -> mapOf("text" to response.asDiarized().text())
                else -> mapOf("text" to "")
            }
            return mapper.writeValueAsString(body)
        }
    }
}
