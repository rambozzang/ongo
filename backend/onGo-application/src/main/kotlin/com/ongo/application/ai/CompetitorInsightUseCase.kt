package com.ongo.application.ai

import com.ongo.application.ai.result.CompetitorInsightResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CompetitorInsightUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val competitorRepository: CompetitorRepository,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(userId: Long): CompetitorInsightResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.COMPETITOR_INSIGHT)

        val channels = channelRepository.findByUserId(userId)
        val mySubscribers = channels.sumOf { it.subscriberCount }
        val allAnalytics = analyticsRepository.findAllByUserId(userId)
        val myTotalViews = allAnalytics.sumOf { it.views.toLong() }
        val myVideoCount = allAnalytics.map { it.videoUploadId }.distinct().size

        val competitors = competitorRepository.findByUserId(userId)
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val competitorDataStr = competitors.joinToString("\n") { comp ->
            val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
                comp.id!!, startDate, endDate
            )
            val firstDay = analytics.firstOrNull()
            val lastDay = analytics.lastOrNull()
            val subGrowth = if (firstDay != null && lastDay != null)
                lastDay.subscriberCount - firstDay.subscriberCount else 0L

            "- ${comp.channelName} (${comp.platform}): 구독자 ${comp.subscriberCount}, 평균 조회수 ${comp.avgViews}, 영상 수 ${comp.videoCount}, 30일 구독자 증감 ${subGrowth}"
        }.ifEmpty { "등록된 경쟁자 없음" }

        val userPrompt = """
내 채널 현황:
- 총 구독자: $mySubscribers
- 총 조회수: $myTotalViews
- 영상 수: $myVideoCount

경쟁자 현황 (최근 30일):
$competitorDataStr

위 데이터를 분석하여 경쟁 벤치마킹 인사이트를 생성해주세요.
""".trimIndent()

        val systemPrompt = """당신은 크리에이터 채널 분석 전문가입니다.
사용자의 채널과 경쟁자 채널 데이터를 비교 분석하여 실질적인 인사이트를 제공합니다.
JSON 형식으로 응답하세요: summary(요약), strengths(강점 리스트), weaknesses(약점 리스트), opportunities(기회 리스트), recommendations(추천 행동 리스트).
각 항목은 2~4개, 구체적이고 실행 가능하게 작성합니다. 한국어로 응답하세요."""

        try {
            return chatClientResolver.resolve(userId).prompt()
                .system(InputSanitizer.sanitize(systemPrompt))
                .user(InputSanitizer.sanitize(userPrompt))
                .call()
                .entity(CompetitorInsightResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("경쟁자 인사이트 생성 실패, 크레딧 환불: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.COMPETITOR_INSIGHT.creditCost, AiFeature.COMPETITOR_INSIGHT.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
