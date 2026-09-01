package com.ongo.application.competitor

import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.ChannelLookupResult
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 수동 동기화가 실제 갱신 결과를 그대로 보고하는지 고정한다.
 *
 * 예전 구현은 `listCompetitors(userId)` 를 그대로 돌려주면서 "동기화가 완료되었습니다"
 * 라고만 답했다. 제공자를 한 번도 부르지 않았으므로 항상 성공으로 보였고, 사용자는
 * 몇 번을 눌러도 값이 그대로인 이유를 알 수 없었다.
 */
class CompetitorUseCaseSyncTest {

    private val competitorRepository = mockk<CompetitorRepository>(relaxed = true)
    private val channelLookupPort = mockk<ChannelLookupPort>()
    private val refreshService = CompetitorRefreshService(competitorRepository, channelLookupPort)

    private val useCase = CompetitorUseCase(
        competitorRepository = competitorRepository,
        channelLookupPort = channelLookupPort,
        competitorRefreshService = refreshService,
        analyticsRepository = mockk<AnalyticsRepository>(relaxed = true),
        videoUploadRepository = mockk<com.ongo.domain.video.VideoUploadRepository>(relaxed = true),
        channelRepository = mockk<ChannelRepository>(relaxed = true),
        subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true),
    )

    private fun competitor(id: Long, platform: String = "YOUTUBE") = Competitor(
        id = id,
        userId = 7L,
        platform = platform,
        platformChannelId = "ch-$id",
        channelName = "채널$id",
    )

    private fun found() = ChannelLookupResult(found = true, subscriberCount = 10, totalViews = 100, videoCount = 5)

    @Test
    @DisplayName("제공자를 실제로 호출해 갱신하고 건수를 그대로 보고한다")
    fun performsRealRefresh() {
        every { competitorRepository.findByUserId(7L) } returns listOf(competitor(1L))
        every { channelLookupPort.lookupChannel("YOUTUBE", "ch-1") } returns found()

        val result = useCase.syncCompetitors(7L)

        verify(exactly = 1) { channelLookupPort.lookupChannel("YOUTUBE", "ch-1") }
        verify(exactly = 1) { competitorRepository.update(any()) }
        assertEquals(1, result.requested)
        assertEquals(1, result.synced)
        assertEquals(0, result.unsupported)
        assertEquals(0, result.failed)
    }

    @Test
    @DisplayName("등록된 경쟁 채널이 없으면 0건으로 정직하게 답한다")
    fun emptyStateIsNotAnError() {
        every { competitorRepository.findByUserId(7L) } returns emptyList()

        val result = useCase.syncCompetitors(7L)

        assertEquals(0, result.requested)
        assertEquals(0, result.synced)
        assertTrue(result.results.isEmpty())
        verify(exactly = 0) { channelLookupPort.lookupChannel(any(), any()) }
    }

    @Test
    @DisplayName("자동 조회 미지원만 있으면 오류가 아니라 갱신 0건으로 알린다")
    fun unsupportedOnlyIsReportedNotThrown() {
        every { competitorRepository.findByUserId(7L) } returns listOf(competitor(2L, "TIKTOK"))
        every { channelLookupPort.lookupChannel("TIKTOK", "ch-2") } returns ChannelLookupResult(
            found = false,
            platform = "TIKTOK",
            requiresManualInput = true,
            message = "이 플랫폼은 자동 채널 조회를 지원하지 않습니다.",
        )

        val result = useCase.syncCompetitors(7L)

        assertEquals(0, result.synced)
        assertEquals(1, result.unsupported)
        assertEquals("UNSUPPORTED", result.results.single().status)
    }

    @Test
    @DisplayName("한 건도 갱신하지 못하고 실패만 있으면 오류를 낸다")
    fun allFailedSurfacesError() {
        every { competitorRepository.findByUserId(7L) } returns listOf(competitor(1L))
        every { channelLookupPort.lookupChannel(any(), any()) } throws RuntimeException("provider down")

        val e = assertFailsWith<BusinessException> { useCase.syncCompetitors(7L) }

        assertEquals("COMPETITOR_SYNC_FAILED", e.code)
    }

    @Test
    @DisplayName("일부만 성공하면 오류로 보지 않는다 - 갱신된 데이터가 실제로 있다")
    fun partialSuccessIsNotAnError() {
        every { competitorRepository.findByUserId(7L) } returns listOf(competitor(1L), competitor(3L))
        every { channelLookupPort.lookupChannel("YOUTUBE", "ch-1") } returns found()
        every { channelLookupPort.lookupChannel("YOUTUBE", "ch-3") } throws RuntimeException("timeout")

        val result = useCase.syncCompetitors(7L)

        assertEquals(1, result.synced)
        assertEquals(1, result.failed)
    }
}
