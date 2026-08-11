package com.ongo.application.competitor

import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.ChannelLookupResult
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 스케줄러가 동결 계정의 데이터를 건드리지 않는지 고정한다.
 *
 * **스케줄러는 HTTP 필터를 지나지 않는다.** `AccountFreezeFilter` 는 요청 경로에만 붙으므로
 * `@Scheduled` 배치는 그대로 통과한다. 그래서 배치 안에서 직접 게이트를 봐야 한다.
 * 이게 "필터 하나로는 부족하다"의 구체적인 사례다.
 */
class CompetitorSyncSchedulerFreezeTest {

    private val repository = mockk<CompetitorRepository>(relaxed = true)
    private val lookup = mockk<ChannelLookupPort>()
    private val guard = mockk<UserWriteGuard>()
    private val refreshService = CompetitorRefreshService(repository, lookup)

    private val scheduler = CompetitorSyncScheduler(repository, refreshService, guard)

    private fun competitor(id: Long, userId: Long) = Competitor(
        id = id,
        userId = userId,
        platform = "YOUTUBE",
        platformChannelId = "ch-$id",
        channelName = "채널$id",
    )

    private fun found() = ChannelLookupResult(
        found = true,
        subscriberCount = 100,
        totalViews = 1000,
        videoCount = 10,
    )

    @Test
    @DisplayName("동결된 계정의 경쟁 채널은 갱신하지 않는다")
    fun frozenUsersCompetitorsAreSkipped() {
        every { repository.findAll() } returns listOf(competitor(1, 100L), competitor(2, 200L))
        every { guard.requireWritable(100L, any(), any()) } throws AccountFrozenException()
        every { guard.requireWritable(200L, any(), any()) } returns Unit
        every { lookup.lookupChannel(any(), any()) } returns found()

        scheduler.syncCompetitorData()

        // 동결 사용자의 것은 조회조차 하지 않는다. 외부 호출도 아끼고 쓰기도 없다.
        verify(exactly = 0) { lookup.lookupChannel("YOUTUBE", "ch-1") }
        verify(exactly = 0) { repository.update(match { it.id == 1L }) }
        verify(exactly = 0) { repository.upsertAnalytics(match { it.competitorId == 1L }) }

        // 정상 사용자의 것은 평소대로 처리된다.
        verify(exactly = 1) { repository.update(match { it.id == 2L }) }
        verify(exactly = 1) { repository.upsertAnalytics(match { it.competitorId == 2L }) }
    }

    @Test
    @DisplayName("게이트 조회가 실패해도 건너뛴다 — fail-closed")
    fun gateLookupFailureSkipsTheUser() {
        every { repository.findAll() } returns listOf(competitor(1, 100L))
        // 가드는 조회 실패도 AccountFrozenException 으로 바꿔 던진다.
        every { guard.requireWritable(100L, any(), any()) } throws
            AccountFrozenException("계정 상태를 확인할 수 없어 요청을 처리하지 못했습니다.")
        every { lookup.lookupChannel(any(), any()) } returns found()

        scheduler.syncCompetitorData()

        // 판정을 못 했으면 쓰지 않는다. 다음 실행에서 다시 시도한다.
        verify(exactly = 0) { repository.update(any()) }
        verify(exactly = 0) { repository.upsertAnalytics(any()) }
    }

    @Test
    @DisplayName("사전 검사는 사용자당 한 번이고, 쓰기 직전 재확인은 항목마다 한다")
    fun precheckIsCachedButRecheckIsNot() {
        every { repository.findAll() } returns
            listOf(competitor(1, 100L), competitor(2, 100L), competitor(3, 100L))
        every { guard.requireWritable(100L, any(), any()) } returns Unit
        every { lookup.lookupChannel(any(), any()) } returns found()

        scheduler.syncCompetitorData()

        // 사전 검사 1회(캐시) + 쓰기 직전 재확인 3회 = 4회.
        // 사전 검사를 캐시하는 것은 외부 호출을 아끼기 위해서지, 쓰기를 안전하게
        // 만들기 위해서가 아니다. 안전은 재확인이 담당한다.
        verify(exactly = 4) { guard.requireWritable(100L, any(), any()) }
        verify(exactly = 3) { repository.update(any()) }
    }

    @Test
    @DisplayName("외부 조회 중 삭제 요청이 들어오면 쓰지 않는다")
    fun deletionRequestedDuringExternalLookupPreventsWrites() {
        every { repository.findAll() } returns listOf(competitor(1, 100L))

        // 사전 검사는 통과했는데 외부 조회 동안 사용자가 탈퇴를 요청한 상황이다.
        // 네트워크 호출이라 수백 ms~수 초가 걸릴 수 있어 실제로 벌어질 수 있는 창이다.
        var calls = 0
        every { guard.requireWritable(100L, any(), any()) } answers {
            calls++
            if (calls > 1) throw AccountFrozenException()
        }
        every { lookup.lookupChannel(any(), any()) } returns found()

        scheduler.syncCompetitorData()

        // 사전 검사만 믿었다면 여기서 동결된 계정에 데이터가 들어갔을 것이다.
        verify(exactly = 1) { lookup.lookupChannel(any(), any()) }
        verify(exactly = 0) { repository.update(any()) }
        verify(exactly = 0) { repository.upsertAnalytics(any()) }
    }

    @Test
    @DisplayName("재확인이 실패해도 쓰지 않는다 — 여기서도 fail-closed")
    fun recheckFailureAlsoPreventsWrites() {
        every { repository.findAll() } returns listOf(competitor(1, 100L))

        var calls = 0
        every { guard.requireWritable(100L, any(), any()) } answers {
            calls++
            // 재확인 시점에 상태 조회가 실패한 경우. 가드가 AccountFrozenException 으로 바꿔 던진다.
            if (calls > 1) throw AccountFrozenException("계정 상태를 확인할 수 없어 요청을 처리하지 못했습니다.")
        }
        every { lookup.lookupChannel(any(), any()) } returns found()

        scheduler.syncCompetitorData()

        verify(exactly = 0) { repository.update(any()) }
        verify(exactly = 0) { repository.upsertAnalytics(any()) }
    }

    @Test
    @DisplayName("전부 동결이어도 스케줄러가 죽지 않는다")
    fun allFrozenDoesNotBreakTheBatch() {
        every { repository.findAll() } returns listOf(competitor(1, 100L), competitor(2, 100L))
        every { guard.requireWritable(any(), any(), any()) } throws AccountFrozenException()

        scheduler.syncCompetitorData()

        verify(exactly = 0) { repository.update(any()) }
    }
}
