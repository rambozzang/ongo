package com.ongo.application.videoseo

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.VideoSeoScoreResult
import com.ongo.application.credit.CreditService
import com.ongo.application.videoseo.dto.VideoSeoScoreResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class VideoSeoUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(VideoSeoUseCase::class.java)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 크레딧 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 직접 잡는다.
     */
    fun analyzeVideoSeo(userId: Long, videoId: Long): VideoSeoScoreResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근할 권한이 없습니다")
        }

        rateLimiter.checkRateLimit(userId)

        val tags = video.tags.joinToString(", ")
        // SEO guidance must follow the video's actual publication targets. Do
        // not charge a creator for recommendations aimed at unrelated channels.
        val platform = videoUploadRepository.findByVideoId(videoId)
            .map { it.platform.name }
            .distinct()
            .joinToString(", ")
            .ifBlank { "플랫폼 미지정" }

        val userPrompt = PromptTemplates.VIDEO_SEO_USER
            .replace("{platform}", platform)
            .replace("{title}", video.title)
            .replace("{description}", video.description ?: "")
            .replace("{tags}", tags)
            .replace("{category}", video.category ?: "미분류")

        return creditService.withCredits(userId, AiFeature.VIDEO_SEO_SCORE) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.VIDEO_SEO_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(VideoSeoScoreResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("비디오 SEO 점수 분석 실패: userId={}, videoId={}", userId, videoId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            VideoSeoScoreResponse(
                videoId = videoId,
                overallScore = result.overallScore,
                titleScore = result.titleScore,
                descriptionScore = result.descriptionScore,
                tagsScore = result.tagsScore,
                generalScore = result.generalScore,
                titleSuggestions = result.titleSuggestions,
                descriptionSuggestions = result.descriptionSuggestions,
                tagsSuggestions = result.tagsSuggestions,
                generalSuggestions = result.generalSuggestions,
                competitorKeywords = result.competitorKeywords,
            )
        }
    }
}
