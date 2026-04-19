package com.ongo.application.benchmark

import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.InputSanitizer
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.EngagementBenchmarkResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.settings.UserSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class EngagementBenchmarkUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(userId: Long): EngagementBenchmarkResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.ENGAGEMENT_BENCHMARK)

        val channels = channelRepository.findByUserId(userId)
        val totalSubscribers = channels.sumOf { it.subscriberCount }
        val category = userSettingsRepository.findByUserId(userId)?.defaultAiTone ?: "일반"

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val crossPlatformMetrics = analyticsRepository.findCrossPlatformMetrics(userId, 30)

        val platformDataStr = if (crossPlatformMetrics.isEmpty()) {
            "데이터 없음"
        } else {
            crossPlatformMetrics
                .groupBy { it.platform }
                .entries
                .joinToString("\n") { (platform, metrics) ->
                    val totalViews = metrics.sumOf { it.views }
                    val totalLikes = metrics.sumOf { it.likes }
                    val totalComments = metrics.sumOf { it.comments }
                    val engRate = if (totalViews > 0)
                        String.format("%.2f", (totalLikes + totalComments).toDouble() / totalViews * 100)
                    else "0.00"
                    "- $platform: 조회수 $totalViews, 좋아요 $totalLikes, 댓글 $totalComments, 참여율 $engRate%"
                }
        }

        val totalViews = crossPlatformMetrics.sumOf { it.views }
        val totalLikes = crossPlatformMetrics.sumOf { it.likes }
        val totalComments = crossPlatformMetrics.sumOf { it.comments }
        val overallEngRate = if (totalViews > 0)
            String.format("%.2f", (totalLikes + totalComments).toDouble() / totalViews * 100)
        else "0.00"
        val avgViews = if (crossPlatformMetrics.isNotEmpty()) totalViews / crossPlatformMetrics.size else 0L
        val avgLikes = if (crossPlatformMetrics.isNotEmpty()) totalLikes / crossPlatformMetrics.size else 0L
        val avgComments = if (crossPlatformMetrics.isNotEmpty()) totalComments / crossPlatformMetrics.size else 0L

        val userPrompt = PromptTemplates.ENGAGEMENT_BENCHMARK_USER
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{subscriberCount}", totalSubscribers.toString())
            .replace("{platformData}", platformDataStr)
            .replace("{avgViews}", avgViews.toString())
            .replace("{avgLikes}", avgLikes.toString())
            .replace("{avgComments}", avgComments.toString())
            .replace("{engagementRate}", overallEngRate)

        try {
            return chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.ENGAGEMENT_BENCHMARK_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(EngagementBenchmarkResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("참여율 벤치마크 분석 실패, 크레딧 환불: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.ENGAGEMENT_BENCHMARK.creditCost, AiFeature.ENGAGEMENT_BENCHMARK.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
