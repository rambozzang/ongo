package com.ongo.application.benchmark

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.estimate.passThroughCredits
import com.ongo.application.ai.estimate.stubChatEntity
import com.ongo.application.ai.result.EngagementBenchmarkResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.settings.UserSettingsRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 참여율 진단의 숫자는 실측만 나간다. AI 가 낸 내 참여율·업계 평균·백분위는 응답에 없다. */
class EngagementBenchmarkMeasuredTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val channelRepository = mockk<ChannelRepository>()
    private val settingsRepository = mockk<UserSettingsRepository>()
    private val useCase = EngagementBenchmarkUseCase(
        chatClientResolver, creditService, mockk<AiRateLimiter>(relaxed = true), channelRepository, analyticsRepository, settingsRepository,
    )

    private fun raw(platform: String, views: Long, likes: Long, comments: Long) = CrossPlatformRaw(
        videoId = 1, videoTitle = "v", platform = platform, videoUploadId = 1,
        views = views, likes = likes, comments = comments, shares = 0,
        watchTimeSeconds = 0, revenueMicro = 0, impressions = 0, avgViewDurationSeconds = 0,
    )

    @Test
    fun `내 참여율은 실측으로 계산하고 AI 값과 업계 비교는 버린다`() {
        every { channelRepository.findByUserId(7) } returns emptyList()
        every { settingsRepository.findByUserId(7) } returns null
        every { analyticsRepository.findCrossPlatformMetrics(7, 30) } returns listOf(
            raw("YOUTUBE", views = 1000, likes = 40, comments = 10),
            raw("TUMBLR", views = 900_000, likes = 60, comments = 0), // 조회수를 수집하지 않는 플랫폼
        )
        passThroughCredits(creditService, 7, AiFeature.ENGAGEMENT_BENCHMARK)
        stubChatEntity(
            chatClientResolver, 7, EngagementBenchmarkResult::class.java,
            EngagementBenchmarkResult(myEngagementRate = 99.0, categoryAverage = 3.2, percentile = 5, strengths = listOf("강점"), improvements = listOf("개선")),
        )

        val response = useCase.execute(7)

        assertEquals(5.0, response.overallEngagementRate) // (40+10)/1000, Tumblr 는 분모가 없어 빠진다
        assertEquals(5.0, response.platforms.single { it.platform == "YOUTUBE" }.engagementRate)
        assertNull(response.platforms.single { it.platform == "TUMBLR" }.engagementRate)
        assertEquals(listOf("강점"), response.strengths)
        assertTrue(response.aiEstimate.basis.contains("업계 평균과 비교한 것이 아닙니다"))
        assertEquals(AiFeature.ENGAGEMENT_BENCHMARK.creditCost, response.creditsUsed)
    }
}
