package com.ongo.application.metarewrite

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.MetaRewriteResult
import com.ongo.application.credit.CreditService
import com.ongo.application.metarewrite.dto.MetaRewriteResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class MetaRewriteUseCase(
    private val videoRepository: VideoRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(MetaRewriteUseCase::class.java)

    @Transactional
    fun rewriteMeta(userId: Long, videoId: Long): MetaRewriteResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근할 권한이 없습니다")
        }

        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.META_REWRITE)

        // 최근 30일 analytics 집계 — 조회수 및 참여율 계산
        val to = LocalDate.now()
        val from = to.minusDays(30)
        val dailyList = analyticsRepository.getDailyAggregates(userId, from, to)
        val totalViews = dailyList.sumOf { it.views }
        val totalLikes = dailyList.sumOf { it.likes }
        val totalComments = dailyList.sumOf { it.comments }
        val engagementRate = if (totalViews > 0) {
            String.format("%.2f", (totalLikes + totalComments).toDouble() / totalViews * 100).toDouble()
        } else {
            0.0
        }

        val platforms = video.title.let { "YouTube, TikTok, Instagram, NAVER_CLIP" }
        val originalTags = video.tags.joinToString(", ")

        val userPrompt = PromptTemplates.META_REWRITE_USER
            .replace("{platform}", platforms)
            .replace("{originalTitle}", video.title)
            .replace("{originalDescription}", video.description ?: "")
            .replace("{originalTags}", originalTags)
            .replace("{totalViews}", totalViews.toString())
            .replace("{engagementRate}", engagementRate.toString())

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.META_REWRITE_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(MetaRewriteResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            return MetaRewriteResponse(
                id = videoId,
                videoId = videoId,
                originalTitle = video.title,
                originalDescription = video.description,
                suggestedTitle = result.suggestedTitle,
                suggestedDescription = result.suggestedDescription,
                suggestedTags = result.suggestedTags,
                reasoning = result.reasoning,
                expectedImpactPercent = result.expectedImpactPercent,
                createdAt = LocalDateTime.now(),
            )
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("메타데이터 리라이트 실패: userId={}, videoId={}", userId, videoId, e)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
