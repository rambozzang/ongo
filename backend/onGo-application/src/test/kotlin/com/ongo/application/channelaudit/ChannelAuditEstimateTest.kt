package com.ongo.application.channelaudit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.estimate.passThroughCredits
import com.ongo.application.ai.estimate.stubChatEntity
import com.ongo.application.ai.result.ChannelAuditResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channelaudit.ChannelAuditReport
import com.ongo.domain.channelaudit.ChannelAuditRepository
import com.ongo.domain.video.Video
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 채널 감사의 숫자 정리: AI 종합 점수는 새로 매기지 않고, 그 자리에 감사 시점의 실측 지표를 남긴다.
 * 예전 보고서의 AI 점수는 지우지 않되 반드시 "AI 추정"으로 표시된다.
 */
class ChannelAuditEstimateTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val auditRepository = mockk<ChannelAuditRepository>()
    private val useCase = ChannelAuditUseCase(chatClientResolver, creditService, rateLimiter, analyticsRepository, channelRepository, auditRepository)

    private val mapper = jacksonObjectMapper()

    private fun aiResult(score: Int?) = ChannelAuditResult(
        overallScore = score,
        strengths = listOf("강점"),
        weaknesses = emptyList(),
        actionItems = emptyList(),
        outlierVideos = emptyList(),
        growthForecast = "전망",
    )

    @Test
    fun `새 보고서는 AI 가 점수를 보내도 버리고, 실측 30일 지표를 함께 저장한다`() {
        every { channelRepository.findByUserId(7) } returns emptyList()
        every { analyticsRepository.getDashboardKpi(7, 30) } returns DashboardKpi(
            totalViews = 1200, totalViewsChange = 12.5, totalSubscribers = null, totalSubscribersChange = null,
            totalLikes = 80, totalLikesChange = null, creditBalance = 0, creditTotal = 0, totalComments = null,
        )
        every { analyticsRepository.getTopVideos(7, 30, 10) } returns listOf(Video(id = 1, userId = 7, title = "a"), Video(id = 2, userId = 7, title = "b"))
        every { analyticsRepository.findCrossPlatformMetrics(7, 30) } returns emptyList()
        passThroughCredits(creditService, 7, AiFeature.CHANNEL_AUDIT)
        val saved = slot<ChannelAuditReport>()
        every { auditRepository.save(capture(saved)) } answers { saved.captured.copy(id = 5) }
        val prompt = stubChatEntity(chatClientResolver, 7, ChannelAuditResult::class.java, aiResult(score = 88))

        val response = useCase.generateAudit(7)

        assertNull(response.overallScore)
        assertNull(response.overallScoreEstimate)
        val measured = assertNotNull(response.measured)
        assertEquals(1200L, measured.totalViews)
        assertEquals(12.5, measured.totalViewsChange)
        assertEquals(80L, measured.totalLikes)
        assertNull(measured.totalComments) // 수집하지 않으면 0 이 아니라 null
        assertEquals(2, measured.analyzedVideoCount)
        assertTrue(response.forecastEstimate.estimated)
        // 저장 본문에 AI 점수가 남지 않고 실측이 들어 있다
        val stored = mapper.readTree(saved.captured.content)
        assertTrue(stored.path("overallScore").isNull)
        assertEquals(1200, stored.path("measured").path("totalViews").asInt())
        // 프롬프트는 점수를 매기지 말라고 말한다(시스템 지시는 별도 시험 대상이 아니라 사용자 입력에 실측이 들어가는지만 본다)
        assertTrue(prompt.captured.contains("최근 30일"))
    }

    @Test
    fun `예전 보고서의 AI 점수는 지우지 않고 추정 표시를 붙여 보여 준다`() {
        every { auditRepository.findById(9) } returns ChannelAuditReport(
            id = 9, userId = 7, overallScore = 72, content = mapper.writeValueAsString(aiResult(score = 72)), createdAt = LocalDateTime.now(),
        )

        val response = useCase.getAuditDetail(7, 9)

        assertEquals(72, response.overallScore)
        assertEquals(ChannelAuditUseCase.LEGACY_SCORE_BASIS, assertNotNull(response.overallScoreEstimate).basis)
        assertNull(response.measured) // 그때는 실측을 저장하지 않았다
        assertEquals(listOf("강점"), response.strengths)
    }

    @Test
    fun `새 보고서를 다시 열어도 실측과 점수 없음이 그대로다`() {
        val content = mapper.valueToTree<com.fasterxml.jackson.databind.node.ObjectNode>(aiResult(score = null))
            .set<com.fasterxml.jackson.databind.node.ObjectNode>(
                "measured",
                mapper.valueToTree(ChannelAuditMeasured(30, 10, null, 1, 0, 50, 3)),
            )
        every { auditRepository.findById(9) } returns ChannelAuditReport(
            id = 9, userId = 7, overallScore = 0, content = mapper.writeValueAsString(content), createdAt = LocalDateTime.now(),
        )

        val response = useCase.getAuditDetail(7, 9)

        assertNull(response.overallScore) // 컬럼의 0 을 점수로 읽지 않는다
        assertEquals(50L, response.measured?.subscriberCount)
        assertEquals(listOf("강점"), response.strengths)
    }

    @Test
    fun `깨진 본문은 점수 없이 빈 보고서로 읽는다 — 컬럼 값으로 점수를 지어내지 않는다`() {
        every { auditRepository.findById(9) } returns ChannelAuditReport(
            id = 9, userId = 7, overallScore = 0, content = "{깨짐", createdAt = LocalDateTime.now(),
        )

        val response = useCase.getAuditDetail(7, 9)

        assertNull(response.overallScore)
        assertFalse(response.strengths.isNotEmpty())
    }

    @Test
    fun `남의 보고서는 열 수 없다`() {
        every { auditRepository.findById(9) } returns ChannelAuditReport(id = 9, userId = 99, overallScore = 0, content = "{}")

        assertEquals("FORBIDDEN", assertFailsWith<BusinessException> { useCase.getAuditDetail(7, 9) }.code)
    }
}
