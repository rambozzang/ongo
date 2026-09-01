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
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.revenue.RevenueInsight
import com.ongo.domain.revenue.RevenueInsightRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡고, 결과 저장은 호출 이후 짧게 끝난다.
     */
    fun generateInsight(userId: Long): RevenueInsightResponse {
        rateLimiter.checkRateLimit(userId)

        // 최근 30일 수익 데이터 조회
        val summary = revenueUseCase.getRevenueSummary(userId, 30)
        if (!summary.platformRevenueAvailable) {
            throw BusinessException(
                "REVENUE_DATA_UNAVAILABLE",
                summary.platformRevenueUnavailableReason
                    ?: "플랫폼 광고 수익 데이터가 연결되지 않아 인사이트를 생성할 수 없습니다.",
            )
        }
        /*
         * **`"${null}%"` 는 문자열 `"null%"` 를 만든다.**
         *
         * 전체 수익이 0 이면 비중은 정의되지 않아 서버가 `null` 을 준다. 예전에는 그
         * 자리가 `0.0` 이라 "비중 0%" 가 프롬프트에 들어갔고, 모델은 그것을 관측으로 읽고
         * 없는 수익 구성을 설명했다. 유료 호출이라 대가까지 치른다.
         *
         * 단위(`%`)는 값이 직접 들고 온다 — 밖에 붙이면 "비중 불가(...)%" 가 된다.
         * 분모가 양수일 때의 `0.0%` 는 실측이므로 그대로 숫자로 간다.
         */
        val platformBreakdown = summary.platformBreakdown.joinToString(", ") { pr ->
            val share = pr.percentage?.let { String.format("%.1f%%", it) } ?: SHARE_NOT_MEASURED
            "${pr.platform}: ${pr.revenueKrw}원 ($share)"
        }

        val userPrompt = PromptTemplates.REVENUE_INSIGHT_USER
            .replace("{days}", "30")
            .replace("{totalRevenue}", summary.totalRevenueKrw.toString())
            // 비교 불가를 숫자나 "null" 문자열로 넣으면 모델이 없는 추세를 설명한다.
            // 단위(%)는 값이 직접 들고 온다.
            .replace("{growthPercent}", MetricChange.describePercent(summary.growthPercent))
            .replace("{platformBreakdown}", platformBreakdown)

        return creditService.withCredits(userId, AiFeature.REVENUE_INSIGHT) {
            val result = try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.REVENUE_INSIGHT_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(RevenueInsightResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("수익 인사이트 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }

            val contentJson = objectMapper.writeValueAsString(result)
            val insight = revenueInsightRepository.save(
                RevenueInsight(
                    userId = userId,
                    insightType = result.insightType,
                    content = contentJson,
                    confidence = BigDecimal.valueOf(result.confidence),
                )
            )
            insight.toResponse()
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

    companion object {
        /**
         * 전체 수익이 0 이라 플랫폼 비중을 낼 수 없을 때 프롬프트에 넣는 문구.
         *
         * 숫자가 아니라 **문장**이어야 한다 — 어떤 숫자를 넣든 모델은 그것을 관측으로
         * 읽는다. 단위(`%`)도 붙이지 않는다.
         */
        const val SHARE_NOT_MEASURED = "비중 산출 불가(전체 수익 0원)"
    }
}
