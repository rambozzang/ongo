package com.ongo.application.ai

import com.ongo.application.ai.dto.WeeklyDigestResponse
import com.ongo.application.ai.result.WeeklyDigestResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.domain.analytics.AnalyticsRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class WeeklyDigestUseCase(
    @Qualifier("anthropicChatClient") private val chatClient: ChatClient,
    private val creditService: CreditService,
    private val analyticsRepository: AnalyticsRepository,
    private val weeklyDigestRepository: WeeklyDigestRepository,
) {

    private val log = LoggerFactory.getLogger(WeeklyDigestUseCase::class.java)

    fun generateDigest(userId: Long, weekStartDate: LocalDate, weekEndDate: LocalDate): WeeklyDigest {
        val kpi = analyticsRepository.getDashboardKpi(userId, 7)
        val prevKpi = analyticsRepository.getDashboardKpi(userId, 14)
        val topVideos = analyticsRepository.getTopVideos(userId, 7, 3)

        val topVideosStr = topVideos.mapIndexed { i, v ->
            "${i + 1}. ${v.title}"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        val viewsChange = if (prevKpi.totalViews > 0) {
            ((kpi.totalViews - prevKpi.totalViews).toDouble() / prevKpi.totalViews * 100)
        } else 0.0

        val likesChange = if (prevKpi.totalLikes > 0) {
            ((kpi.totalLikes - prevKpi.totalLikes).toDouble() / prevKpi.totalLikes * 100)
        } else 0.0

        val userPrompt = PromptTemplates.WEEKLY_DIGEST_USER
            .replace("{weekStart}", weekStartDate.toString())
            .replace("{weekEnd}", weekEndDate.toString())
            .replace("{totalViews}", kpi.totalViews.toString())
            .replace("{viewsChange}", String.format("%.1f", viewsChange))
            .replace("{totalLikes}", kpi.totalLikes.toString())
            .replace("{likesChange}", String.format("%.1f", likesChange))
            .replace("{totalComments}", "N/A")
            .replace("{subscriberChange}", kpi.totalSubscribersChange.toString())
            .replace("{topVideos}", topVideosStr)

        try {
            val result = chatClient.prompt()
                .system(PromptTemplates.WEEKLY_DIGEST_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(WeeklyDigestResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            val digest = WeeklyDigest(
                userId = userId,
                weekStartDate = weekStartDate,
                weekEndDate = weekEndDate,
                summary = result.summary,
                topVideos = result.topVideos.joinToString("\n") { "${it.title}: ${it.insight}" },
                anomalies = result.anomalies.joinToString("\n"),
                actionItems = result.actionItems.joinToString("\n"),
                generatedAt = LocalDateTime.now(),
            )

            return weeklyDigestRepository.save(digest)
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("주간 다이제스트 생성 실패: userId={}", userId, e)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }

    fun getLatestDigest(userId: Long): WeeklyDigestResponse {
        val digest = weeklyDigestRepository.findLatestByUserId(userId)
            ?: throw NotFoundException("주간 다이제스트", 0)
        return digest.toResponse()
    }

    fun listDigests(userId: Long, page: Int, size: Int): List<WeeklyDigestResponse> {
        return weeklyDigestRepository.findByUserId(userId, page, size)
            .map { it.toResponse() }
    }

    private fun WeeklyDigest.toResponse() = WeeklyDigestResponse(
        id = id!!,
        weekRange = "$weekStartDate ~ $weekEndDate",
        summary = summary,
        topVideos = topVideos.split("\n").filter { it.isNotBlank() },
        anomalies = anomalies.split("\n").filter { it.isNotBlank() },
        actionItems = actionItems.split("\n").filter { it.isNotBlank() },
        generatedAt = generatedAt,
    )
}
