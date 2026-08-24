package com.ongo.application.ai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.openai.models.audio.AudioResponseFormat
import com.ongo.application.ai.audio.AudioPreparationException
import com.ongo.application.ai.audio.TranscriptionAudioPort
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
import org.springframework.stereotype.Service

@Service
class SttUseCase(
    private val transcriptionModel: OpenAiAudioTranscriptionModel,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val videoRepository: VideoRepository,
    private val audioPort: TranscriptionAudioPort,
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

        return transcribe(fileUrl)
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

        try {
            return transcribe(fileUrl)
        } catch (e: BusinessException) {
            // 준비 실패(인코더 부재·추출 실패)도 사용자 잘못이 아니다. 차감한 크레딧을 돌려준다.
            log.error("STT 처리 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.STT.creditCost, AiFeature.STT.name)
            throw e
        } catch (e: Exception) {
            log.error("STT 처리 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.STT.creditCost, AiFeature.STT.name)
            throw BusinessException("AI_CALL_FAILED", "음성 인식에 실패했습니다: ${e.message}")
        }
    }

    /**
     * 원본에서 오디오를 뽑아 조각별로 전사한 뒤 하나로 합친다.
     *
     * ## 왜 원본 URL 을 그대로 넘기지 않는가
     *
     * 이전에는 영상 파일 URL 을 전사 모델에 통째로 넘겼다. 영상 트랙까지 올리는 데다
     * 길이 상한도 없어서, 실제 롱폼 원본에서는 요청이 거부되거나 API 서버가 파일 전체를
     * 중계하며 대역폭과 힙을 함께 썼다. 조각 크기는 제공자 한도를 추측하지 않고
     * **우리 설정값**으로 보수적으로 묶는다.
     *
     * ## 순차 처리인 이유
     *
     * 조각을 동시에 보내면 빨라지지만 사용자 한 명이 제공자 rate limit 을 독식한다.
     * 파이프라인은 이미 비동기라 여기서 서두를 이유가 없다.
     */
    private fun transcribe(sourceUrl: String): SttResult {
        if (!audioPort.isAvailable()) {
            // 준비가 안 되는 걸 알면서 모델을 부르면 요금만 나가고 결과는 같다.
            throw BusinessException(
                "STT_ENCODER_UNAVAILABLE",
                "음성 인식을 지금 사용할 수 없습니다. 잠시 후 다시 시도하거나 고객지원에 문의해 주세요.",
            )
        }

        val prepared = try {
            audioPort.prepare(sourceUrl)
        } catch (e: AudioPreparationException) {
            throw BusinessException(
                "STT_AUDIO_PREPARATION_FAILED",
                "원본에서 음성을 추출하지 못했습니다. 파일이 손상되었거나 음성이 없는지 확인해 주세요.",
            )
        }

        // 조각은 로컬 임시 파일이다. 성공하든 실패하든 반드시 지운다.
        return prepared.use { audio ->
            val options = OpenAiAudioTranscriptionOptions.builder()
                .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                .language("ko")
                .build()

            val texts = mutableListOf<String>()
            val segments = mutableListOf<SttResult.SttSegmentResult>()

            audio.parts.forEach { part ->
                val response: AudioTranscriptionResponse =
                    transcriptionModel.call(AudioTranscriptionPrompt(part.resource, options))
                val partText = response.result.output

                texts += parseText(partText)
                /*
                 * 조각 전사의 타임스탬프는 조각 기준(0부터)이다. 오프셋을 더하지 않으면
                 * 두 번째 조각부터 자막이 전부 영상 앞머리로 겹쳐 붙는다.
                 */
                val offsetSeconds = part.offsetMs / 1000.0
                segments += parseSegments(partText).map { segment ->
                    segment.copy(
                        startTime = segment.startTime + offsetSeconds,
                        endTime = segment.endTime + offsetSeconds,
                    )
                }
            }

            SttResult(
                text = texts.filter { it.isNotBlank() }.joinToString(" "),
                segments = segments,
            )
        }
    }

    /**
     * VERBOSE_JSON 응답에서 사람이 읽는 본문만 꺼낸다.
     *
     * 조각이 하나였을 때는 응답 원문을 그대로 [SttResult.text] 에 담아도 티가 나지 않았지만,
     * 여러 조각을 이어붙이면 JSON 문서 여러 개가 나란히 붙은 문자열이 된다. 이 값은 뒤 단계
     * 프롬프트의 입력이라 그대로 두면 전사 품질이 아니라 형식 때문에 결과가 나빠진다.
     * 파싱이 안 되면 원문을 그대로 쓴다 — 버리는 것보다 낫다.
     */
    private fun parseText(responseText: String): String =
        try {
            objectMapper.readValue<Map<String, Any>>(responseText)["text"] as? String ?: responseText
        } catch (_: Exception) {
            responseText
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
