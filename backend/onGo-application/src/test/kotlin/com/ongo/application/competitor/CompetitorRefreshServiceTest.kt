package com.ongo.application.competitor

import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.ChannelLookupResult
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * 갱신 결과를 성공/미지원/실패로 구분하는지 고정한다.
 *
 * 셋을 뭉뚱그리면 호출자가 정직한 답을 만들 수 없다. 특히 "자동 조회 미지원"은
 * 재시도해도 같은 결과이므로 실패와 같이 다루면 사용자에게 잘못된 안내가 나간다.
 */
class CompetitorRefreshServiceTest {

    private val repository = mockk<CompetitorRepository>(relaxed = true)
    private val lookup = mockk<ChannelLookupPort>()
    private val service = CompetitorRefreshService(repository, lookup)

    private val today = LocalDate.of(2026, 8, 11)

    private fun competitor(id: Long = 1L, platform: String = "YOUTUBE") = Competitor(
        id = id,
        userId = 7L,
        platform = platform,
        platformChannelId = "ch-$id",
        channelName = "채널$id",
    )

    @Test
    @DisplayName("조회에 성공하면 스냅샷과 일별 집계를 모두 저장하고 SYNCED 를 돌려준다")
    fun syncedPersistsBothRows() {
        every { lookup.lookupChannel("YOUTUBE", "ch-1") } returns ChannelLookupResult(
            found = true,
            subscriberCount = 1_000,
            totalViews = 5_000,
            videoCount = 10,
        )

        val outcome = service.refresh(competitor(), today)

        assertEquals(CompetitorRefreshStatus.SYNCED, outcome.status)
        // avgViews 는 파생값이라 저장 시점에 계산해야 한다. 스케줄러와 HTTP 경로가
        // 각자 계산하면 갈라지는 지점이었다.
        verify(exactly = 1) { repository.update(match { it.avgViews == 500L && it.lastSyncedAt != null }) }
        verify(exactly = 1) {
            repository.upsertAnalytics(match { it.competitorId == 1L && it.date == today && it.avgViews == 500L })
        }
    }

    @Test
    @DisplayName("영상이 0건이면 평균 조회수를 0으로 둔다 (0 나눗셈 없음)")
    fun zeroVideoCountDoesNotDivide() {
        every { lookup.lookupChannel(any(), any()) } returns ChannelLookupResult(
            found = true,
            subscriberCount = 10,
            totalViews = 100,
            videoCount = 0,
        )

        val outcome = service.refresh(competitor(), today)

        assertEquals(CompetitorRefreshStatus.SYNCED, outcome.status)
        verify { repository.update(match { it.avgViews == 0L }) }
    }

    @Test
    @DisplayName("자동 조회를 지원하지 않으면 UNSUPPORTED 로 알리고 아무것도 쓰지 않는다")
    fun unsupportedPlatformWritesNothing() {
        every { lookup.lookupChannel("TIKTOK", "ch-2") } returns ChannelLookupResult(
            found = false,
            platform = "TIKTOK",
            requiresManualInput = true,
            message = "이 플랫폼은 자동 채널 조회를 지원하지 않습니다.",
        )

        val outcome = service.refresh(competitor(id = 2L, platform = "TIKTOK"), today)

        assertEquals(CompetitorRefreshStatus.UNSUPPORTED, outcome.status)
        assertEquals("이 플랫폼은 자동 채널 조회를 지원하지 않습니다.", outcome.message)
        verify(exactly = 0) { repository.update(any()) }
        verify(exactly = 0) { repository.upsertAnalytics(any()) }
    }

    @Test
    @DisplayName("조회가 예외로 끝나면 FAILED 로 알리고 다른 건은 계속 처리한다")
    fun failureIsReportedAndDoesNotStopTheBatch() {
        every { lookup.lookupChannel("YOUTUBE", "ch-1") } throws RuntimeException("provider down")
        every { lookup.lookupChannel("YOUTUBE", "ch-3") } returns ChannelLookupResult(
            found = true,
            subscriberCount = 1,
            totalViews = 2,
            videoCount = 1,
        )

        val summary = service.refreshAll(listOf(competitor(1L), competitor(3L)), today)

        assertEquals(2, summary.requested)
        assertEquals(1, summary.synced)
        assertEquals(1, summary.failed)
        assertEquals(0, summary.unsupported)
        assertEquals(CompetitorRefreshStatus.FAILED, summary.outcomes.first { it.competitorId == 1L }.status)
        // 실패한 건은 저장되지 않아야 한다.
        verify(exactly = 0) { repository.update(match { it.id == 1L }) }
    }
}
