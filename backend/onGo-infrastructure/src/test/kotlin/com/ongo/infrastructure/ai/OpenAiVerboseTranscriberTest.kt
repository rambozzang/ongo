package com.ongo.infrastructure.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.models.audio.AudioResponseFormat
import com.openai.models.audio.transcriptions.Transcription
import com.openai.models.audio.transcriptions.TranscriptionCreateParams.TimestampGranularity
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse
import com.openai.models.audio.transcriptions.TranscriptionSegment
import com.openai.models.audio.transcriptions.TranscriptionVerbose
import com.openai.models.audio.transcriptions.TranscriptionWord
import org.junit.jupiter.api.Test
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions
import org.springframework.core.io.ByteArrayResource
import kotlin.test.assertEquals

/**
 * 전사 응답을 **시각까지 담은 원문 JSON** 으로 돌려주는지 고정한다(네트워크 없이).
 * Spring AI 2.0 모델은 이 정보를 버려서 쇼츠 자막이 비었다.
 */
class OpenAiVerboseTranscriberTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `상세 응답은 문장과 단어 시각을 API 필드 이름 그대로 담는다`() {
        val verbose = TranscriptionVerbose.builder()
            .duration(1.2)
            .language("korean")
            .text("안녕 하세요")
            .addSegment(
                TranscriptionSegment.builder()
                    .id(0).seek(0).start(0.0).end(1.2).text("안녕 하세요")
                    .tokens(listOf(1L, 2L)).temperature(0f).avgLogprob(-0.1f).compressionRatio(1f).noSpeechProb(0.01f)
                    .build(),
            )
            .addWord(TranscriptionWord.builder().word("안녕").start(0.0).end(0.5).build())
            .addWord(TranscriptionWord.builder().word("하세요").start(0.5).end(1.2).build())
            .build()

        val json = mapper.readTree(OpenAiVerboseTranscriber.toJson(TranscriptionCreateResponse.ofVerbose(verbose)))

        assertEquals("안녕 하세요", json.path("text").asText())
        assertEquals(1.2, json.path("segments")[0].path("end").asDouble())
        assertEquals(listOf("안녕", "하세요"), json.path("words").map { it.path("word").asText() })
        assertEquals(0.5, json.path("words")[1].path("start").asDouble())
    }

    @Test
    fun `본문만 있는 응답은 text 만 담는다`() {
        val json = OpenAiVerboseTranscriber.toJson(TranscriptionCreateResponse.ofTranscription(Transcription.builder().text("본문").build()))

        assertEquals("본문", mapper.readTree(json).path("text").asText())
    }

    @Test
    fun `요청에는 모델 형식 언어 시각 단위가 그대로 실린다`() {
        val options = OpenAiAudioTranscriptionOptions.builder()
            .model("whisper-1")
            .language("ko")
            .responseFormat(AudioResponseFormat.VERBOSE_JSON)
            .timestampGranularities(listOf(TimestampGranularity.WORD, TimestampGranularity.SEGMENT))
            .build()
        val resource = object : ByteArrayResource("audio".toByteArray()) {
            override fun getFilename() = "part-0.mp3"
        }

        val params = OpenAiVerboseTranscriber.buildParams(AudioTranscriptionPrompt(resource, options))

        assertEquals("whisper-1", params.model().asString())
        assertEquals(AudioResponseFormat.VERBOSE_JSON, params.responseFormat().get())
        assertEquals("ko", params.language().get())
        assertEquals(listOf(TimestampGranularity.WORD, TimestampGranularity.SEGMENT), params.timestampGranularities().get())
    }
}
