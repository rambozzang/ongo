package com.ongo.application.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.application.ai.result.SttResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions
import org.springframework.ai.openai.api.OpenAiAudioApi
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service

@Service
class SttUseCase(
    private val transcriptionModel: OpenAiAudioTranscriptionModel,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val videoRepository: VideoRepository,
) {

    private val log = LoggerFactory.getLogger(SttUseCase::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * Pipeline-internal execution: skips rate-limit check and credit deduction
     * (credits are pre-reserved by the pipeline).
     */
    fun executeInternal(userId: Long, videoId: Long): SttResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근 권한이 없습니다")
        }

        val fileUrl = video.fileUrl
            ?: throw BusinessException("VIDEO_FILE_NOT_FOUND", "영상 파일 URL이 없습니다. videoId: $videoId")

        val options = OpenAiAudioTranscriptionOptions.builder()
            .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.VERBOSE_JSON)
            .language("ko")
            .build()

        val resource = UrlResource(fileUrl)
        val prompt = AudioTranscriptionPrompt(resource, options)

        val response: AudioTranscriptionResponse = transcriptionModel.call(prompt)
        val text = response.result.output
        val segments = parseSegments(text)
        return SttResult(text = text, segments = segments)
    }

    fun execute(userId: Long, videoId: Long): SttResult {
        rateLimiter.checkRateLimit(userId)

        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근 권한이 없습니다")
        }

        val fileUrl = video.fileUrl
            ?: throw BusinessException("VIDEO_FILE_NOT_FOUND", "영상 파일 URL이 없습니다. videoId: $videoId")

        creditService.validateAndDeduct(userId, AiFeature.STT)

        val options = OpenAiAudioTranscriptionOptions.builder()
            .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.VERBOSE_JSON)
            .language("ko")
            .build()

        val resource = UrlResource(fileUrl)
        val prompt = AudioTranscriptionPrompt(resource, options)

        try {
            val response: AudioTranscriptionResponse = transcriptionModel.call(prompt)

            val text = response.result.output
            val segments = parseSegments(text)

            return SttResult(text = text, segments = segments)
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("STT 처리 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.STT.creditCost, AiFeature.STT.name)
            throw BusinessException("AI_CALL_FAILED", "음성 인식에 실패했습니다: ${e.message}")
        }
    }

    private fun parseSegments(responseText: String): List<SttResult.SttSegmentResult> {
        return try {
            val json = objectMapper.readValue<Map<String, Any>>(responseText)
            @Suppress("UNCHECKED_CAST")
            val segmentsList = json["segments"] as? List<Map<String, Any>> ?: return emptyList()

            segmentsList.map { segment ->
                SttResult.SttSegmentResult(
                    startTime = (segment["start"] as? Number)?.toDouble() ?: 0.0,
                    endTime = (segment["end"] as? Number)?.toDouble() ?: 0.0,
                    text = segment["text"] as? String ?: "",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
