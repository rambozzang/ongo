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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VideoSeoUseCase(
    private val videoRepository: VideoRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(VideoSeoUseCase::class.java)

    @Transactional
    fun analyzeVideoSeo(userId: Long, videoId: Long): VideoSeoScoreResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근할 권한이 없습니다")
        }

        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.VIDEO_SEO_SCORE)

        val tags = video.tags.joinToString(", ")
        val platform = "YouTube, TikTok, Instagram"

        val userPrompt = PromptTemplates.VIDEO_SEO_USER
            .replace("{platform}", platform)
            .replace("{title}", video.title)
            .replace("{description}", video.description ?: "")
            .replace("{tags}", tags)
            .replace("{category}", video.category ?: "미분류")

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.VIDEO_SEO_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(VideoSeoScoreResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            return VideoSeoScoreResponse(
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
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("비디오 SEO 점수 분석 실패, 크레딧 환불 처리: userId={}, videoId={}", userId, videoId, e)
            creditService.refundCredit(userId, AiFeature.VIDEO_SEO_SCORE.creditCost, AiFeature.VIDEO_SEO_SCORE.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
