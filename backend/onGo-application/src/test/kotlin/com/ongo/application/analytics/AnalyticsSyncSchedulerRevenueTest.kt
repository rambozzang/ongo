package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.RevenueMeasurement
import com.ongo.domain.analytics.RevenueReport
import com.ongo.domain.analytics.RevenueStatus
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.PlatformAnalyticsResult
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 수익 동기화가 일반 분석과 **분리돼 있는지** 고정한다.
 *
 * 예전 구조라면 수익 지표를 같은 질의에 넣는 순간 금전 scope 하나 때문에 조회수·좋아요·
 * 댓글까지 전부 저장되지 않았다. 그 회귀를 여기서 잡는다.
 */
class AnalyticsSyncSchedulerRevenueTest {

    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val analyticsRepository = mockk<AnalyticsRepository>(relaxed = true)
    private val platformClientPort = mockk<PlatformClientPort>()
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()
    private val lockPort = mockk<DistributedLockPort>()
    private val guard = mockk<UserWriteGuard>()

    private val userId = 100L
    private val uploadId = 55L

    private fun scheduler() = AnalyticsSyncScheduler(
        channelRepository = channelRepository,
        videoUploadRepository = videoUploadRepository,
        analyticsRepository = analyticsRepository,
        platformClientPort = platformClientPort,
        tokenEncryptionPort = tokenEncryptionPort,
        distributedLockPort = lockPort,
        userWriteGuard = guard,
    ).also {
        every { lockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
    }

    private fun stubChannel(platform: Platform) {
        every { channelRepository.findAllActive() } returns listOf(
            Channel(
                id = 1L,
                userId = userId,
                platform = platform,
                platformChannelId = "ch-1",
                channelName = "채널",
                accessToken = EncryptedToken("enc"),
            ),
        )
        every { guard.requireWritable(userId, any(), any()) } returns Unit
        every { tokenEncryptionPort.decrypt(EncryptedToken("enc")) } returns PlainToken("token")
        every { videoUploadRepository.findByPlatformAndUserId(platform, userId) } returns listOf(
            VideoUpload(id = uploadId, videoId = 7L, platform = platform, platformVideoId = "vid-1"),
        )
        // 백필이 돌지 않도록 최근 창을 모두 채워 둔다. 이 테스트의 관심사는 수익이다.
        val yesterday = LocalDate.now().minusDays(1)
        every { analyticsRepository.findLatestDateByVideoUploadId(uploadId) } returns yesterday
        every { analyticsRepository.findByVideoUploadIdAndDateRange(uploadId, any(), any()) } answers {
            val from = secondArg<LocalDate>()
            val to = thirdArg<LocalDate>()
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .map { AnalyticsDaily(videoUploadId = uploadId, date = it) }
                .toList()
        }
        every {
            platformClientPort.getVideoAnalytics(any(), any(), any(), any(), any())
        } returns PlatformAnalyticsResult(
            views = 120, likes = 9, comments = 4, shares = 2,
            watchTimeSeconds = 600, subscriberGained = 7,
            impressions = 500, avgViewDurationSeconds = 33,
        )
    }

    /**
     * **요구사항의 핵심.** 금전 scope 403 이어도 8개 일반 지표는 저장돼야 하고,
     * 수익만 PERMISSION_REQUIRED 로 남아야 한다.
     */
    @Test
    @DisplayName("수익이 403 이어도 일반 분석은 저장되고 수익만 PERMISSION_REQUIRED 다")
    fun revenuePermissionFailureDoesNotBlockGeneralAnalytics() {
        stubChannel(Platform.YOUTUBE)
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), any())
        } returns RevenueReport.PERMISSION_REQUIRED

        val saved = slot<AnalyticsDaily>()
        every { analyticsRepository.upsert(capture(saved)) } answers { firstArg() }
        val measurements = mutableListOf<RevenueMeasurement>()
        every {
            analyticsRepository.updateRevenue(eq(uploadId), any(), capture(measurements))
        } returns true

        scheduler().syncAnalytics()

        // 8개 지표가 전부 살아 있다.
        val analytics = saved.captured
        assertEquals(120, analytics.views)
        assertEquals(9, analytics.likes)
        assertEquals(4, analytics.commentsCount)
        assertEquals(2, analytics.shares)
        assertEquals(600L, analytics.watchTimeSeconds)
        assertEquals(7, analytics.subscriberGained)
        assertEquals(500, analytics.impressions)
        assertEquals(33, analytics.avgViewDurationSeconds)

        // 수익만 권한 부족으로 기록된다. 0 원으로 저장하지 않는다.
        assertTrue(measurements.isNotEmpty())
        assertTrue(measurements.all { it.status == RevenueStatus.PERMISSION_REQUIRED })
        assertTrue(measurements.all { it.amountMicro == null })
    }

    /** 응답에 있는 날짜만 실측이다. 나머지를 0 원으로 채우면 확정 전 금액이 굳는다. */
    @Test
    @DisplayName("응답에 없는 날짜는 PENDING 으로 남긴다")
    fun missingDaysStayPending() {
        stubChannel(Platform.YOUTUBE)
        val measuredDay = LocalDate.now().minusDays(2)
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), any())
        } returns RevenueReport.measured(
            mapOf(measuredDay to RevenueMeasurement.measured(15_230_500_000L, "KRW")),
        )

        val byDate = mutableMapOf<LocalDate, RevenueMeasurement>()
        val dates = mutableListOf<LocalDate>()
        val values = mutableListOf<RevenueMeasurement>()
        every { analyticsRepository.updateRevenue(eq(uploadId), capture(dates), capture(values)) } returns true

        scheduler().syncAnalytics()

        dates.forEachIndexed { i, d -> byDate[d] = values[i] }

        assertEquals(RevenueStatus.MEASURED, byDate.getValue(measuredDay).status)
        assertEquals(15_230_500_000L, byDate.getValue(measuredDay).amountMicro)
        assertEquals("KRW", byDate.getValue(measuredDay).currency)

        val otherDay = LocalDate.now().minusDays(5)
        assertEquals(RevenueStatus.PENDING, byDate.getValue(otherDay).status)
        assertEquals(null, byDate.getValue(otherDay).amountMicro)
    }

    /** 최근 30일을 다시 묻는다. 확정 전 값이 영구히 굳으면 안 된다. */
    @Test
    @DisplayName("이미 저장된 날짜도 최근 30일은 다시 조회한다")
    fun recentDaysAreAlwaysRequeried() {
        stubChannel(Platform.YOUTUBE)
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), any())
        } returns RevenueReport.PENDING

        val dates = mutableListOf<LocalDate>()
        every { analyticsRepository.updateRevenue(eq(uploadId), capture(dates), any()) } returns true

        scheduler().syncAnalytics()

        val yesterday = LocalDate.now().minusDays(1)
        assertEquals(30, dates.size)
        assertEquals(yesterday, dates.max())
        assertEquals(yesterday.minusDays(29), dates.min())
    }

    /** 오늘은 항상 비어 있다. 물어봐야 PENDING 만 늘어난다. */
    @Test
    @DisplayName("오늘 날짜는 수익 조회 범위에 넣지 않는다")
    fun todayIsExcluded() {
        stubChannel(Platform.YOUTUBE)
        val end = slot<LocalDate>()
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), capture(end))
        } returns RevenueReport.PENDING

        scheduler().syncAnalytics()

        assertEquals(LocalDate.now().minusDays(1), end.captured)
    }

    /**
     * 수익을 수집하지 않는 플랫폼은 부르지도 쓰지도 않는다. DB 기본값이 이미
     * UNSUPPORTED 라 결과가 같고, 30일치 무의미한 쓰기만 남는다.
     */
    @Test
    @DisplayName("TikTok 채널은 수익을 조회하지 않는다")
    fun unsupportedPlatformIsNotQueried() {
        stubChannel(Platform.TIKTOK)

        scheduler().syncAnalytics()

        verify(exactly = 0) { platformClientPort.getVideoRevenue(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { analyticsRepository.updateRevenue(any(), any(), any()) }
        // 일반 분석은 그대로 돈다.
        verify(atLeast = 1) { analyticsRepository.upsert(any()) }
    }

    /**
     * 수익 동기화는 **분석 행을 만들지 않는다.**
     *
     * 예전 upsert 구조에서는 조회 기간 30일 전체에 행이 생겼고, 그 행들은 조회수 0 에
     * `created_at` 이 동기화 시각이었다. `getOptimalPublishTimes` 는 `created_at` 의 시각을
     * 슬롯 키로 쓰고 최근 30일에 가중치 3을 주므로, 그 가짜 행들이 "스케줄러가 도는 시각"을
     * 최적 업로드 시간으로 밀어 올렸다.
     */
    @Test
    @DisplayName("수익 동기화는 분석 행을 새로 만들지 않는다")
    fun revenueSyncNeverCreatesAnalyticsRows() {
        stubChannel(Platform.YOUTUBE)
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), any())
        } returns RevenueReport.PENDING

        // 분석 행이 없는 날짜라 갱신 대상이 0 행이다.
        every { analyticsRepository.updateRevenue(any(), any(), any()) } returns false

        val savedAnalytics = mutableListOf<AnalyticsDaily>()
        every { analyticsRepository.upsert(capture(savedAnalytics)) } answers { firstArg() }

        scheduler().syncAnalytics()

        // 수익 경로는 갱신만 시도한다. 행 생성 경로(upsert)는 일반 분석이 부른 것뿐이다.
        verify(atLeast = 1) { analyticsRepository.updateRevenue(eq(uploadId), any(), any()) }
        // 일반 분석이 만든 행은 오늘 하나뿐이다 — 수익 경로가 30일치를 추가하지 않았다.
        assertEquals(listOf(LocalDate.now()), savedAnalytics.map { it.date })
    }

    /** 갱신 대상이 없어도(0 행) 배치는 조용히 계속된다. */
    @Test
    @DisplayName("갱신된 행이 없어도 동기화가 중단되지 않는다")
    fun missingRowsDoNotBreakTheBatch() {
        stubChannel(Platform.YOUTUBE)
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), any())
        } returns RevenueReport.measured(
            mapOf(LocalDate.now().minusDays(2) to RevenueMeasurement.measured(1_000_000L, "KRW")),
        )
        every { analyticsRepository.updateRevenue(any(), any(), any()) } returns false

        scheduler().syncAnalytics()

        verify(exactly = 30) { analyticsRepository.updateRevenue(eq(uploadId), any(), any()) }
        verify(atLeast = 1) { analyticsRepository.upsert(any()) }
    }

    /**
     * **중간 누락 복구.**
     *
     * 예전에는 `MAX(date) + 1` 부터 앞으로만 훑어서, 날짜 D 가 실패한 뒤 D+1 이 저장되면
     * D 를 영원히 건너뛰었다. 수익은 분석 행이 있어야 갱신되므로 그 날짜의 수익도 함께
     * 영구 유실됐다.
     */
    @Test
    @DisplayName("최신일 이전의 빈 날짜를 다음 실행에서 다시 조회한다")
    fun refetchesGapBeforeWatermark() {
        stubChannel(Platform.YOUTUBE)
        val today = LocalDate.now()
        val gap = today.minusDays(4)
        // 창 안이 모두 채워져 있고 gap 하루만 비어 있다. MAX(date) 는 어제다.
        every { analyticsRepository.findLatestDateByVideoUploadId(uploadId) } returns today.minusDays(1)
        every { analyticsRepository.findByVideoUploadIdAndDateRange(uploadId, any(), any()) } answers {
            val from = secondArg<LocalDate>()
            val to = thirdArg<LocalDate>()
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .filter { it != gap }
                .map { AnalyticsDaily(videoUploadId = uploadId, date = it) }
                .toList()
        }
        every {
            platformClientPort.getVideoRevenue(any(), any(), any(), any(), any())
        } returns RevenueReport.PENDING
        every { analyticsRepository.updateRevenue(any(), any(), any()) } returns true

        val requested = mutableListOf<LocalDate>()
        every {
            platformClientPort.getVideoAnalytics(any(), any(), any(), capture(requested), any())
        } returns PlatformAnalyticsResult(
            views = 10, likes = 1, comments = 0, shares = 0,
            watchTimeSeconds = 60, subscriberGained = 0,
        )

        scheduler().syncAnalytics()

        assertTrue(requested.contains(gap), "빈 날짜를 다시 조회하지 않았습니다: $requested")
        // 오늘 조회는 상한과 별개로 한 번 더 붙는다.
        assertTrue(requested.contains(today), "오늘 조회가 사라졌습니다: $requested")
        // 이미 채워진 날짜는 다시 부르지 않는다 — 빈 날짜 1개 + 오늘 1개.
        assertEquals(2, requested.size, "불필요한 재조회가 있습니다: $requested")
    }
}
