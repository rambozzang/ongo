package com.ongo.application.revenue

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.RevenueInsightResult
import com.ongo.application.credit.CreditService
import com.ongo.application.revenue.dto.RevenueInsightListResponse
import com.ongo.application.revenue.dto.RevenueInsightResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.revenue.RevenueInsight
import com.ongo.domain.revenue.RevenueInsightRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class RevenueInsightUseCase(
    private val revenueInsightRepository: RevenueInsightRepository,
    private val revenueUseCase: RevenueUseCase,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(RevenueInsightUseCase::class.java)

    fun getInsights(userId: Long, page: Int, size: Int): RevenueInsightListResponse {
        val insights = revenueInsightRepository.findByUserId(userId, page, size)
        val total = revenueInsightRepository.countByUserId(userId)
        return RevenueInsightListResponse(
            insights = insights.map { it.toResponse() },
            totalElements = total,
            page = page,
            size = size,
        )
    }

    @Transactional
    fun generateInsight(userId: Long): RevenueInsightResponse {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.REVENUE_INSIGHT)

        // 최근 30일 수익 데이터 조회
        val summary = revenueUseCase.getRevenueSummary(userId, 30)
        val platformBreakdown = summary.platformBreakdown.joinToString(", ") { pr ->
            "${pr.platform}: ${pr.revenueKrw}원 (${pr.percentage}%)"
        }

        val userPrompt = PromptTemplates.REVENUE_INSIGHT_USER
            .replace("{days}", "30")
            .replace("{totalRevenue}", summary.totalRevenueKrw.toString())
            .replace("{growthPercent}", summary.growthPercent.toString())
            .replace("{platformBreakdown}", platformBreakdown)

        try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.REVENUE_INSIGHT_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(RevenueInsightResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            val contentJson = objectMapper.writeValueAsString(result)
            val insight = revenueInsightRepository.save(
                RevenueInsight(
                    userId = userId,
                    insightType = result.insightType,
                    content = contentJson,
                    confidence = BigDecimal.valueOf(result.confidence),
                )
            )
            return insight.toResponse()
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            // @Transactional 롤백으로 크레딧 차감 자체가 취소되므로 별도 환불 불필요
            log.error("수익 인사이트 생성 실패: userId={}", userId, e)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }

    private fun RevenueInsight.toResponse() = RevenueInsightResponse(
        id = id!!,
        insightType = insightType,
        content = content,
        platform = platform,
        confidence = confidence,
        createdAt = createdAt,
    )
}
