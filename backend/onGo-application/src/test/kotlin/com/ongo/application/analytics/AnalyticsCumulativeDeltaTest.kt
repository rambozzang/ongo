package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.EngagementBasis
import com.ongo.domain.analytics.EngagementTotals
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 평생 누적값을 **일별 증분으로 바꿔 저장하는지** 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * 어댑터 13개 중 YouTube 하나만 기간값을 준다. 나머지 12개는 날짜를 시그니처로 받기만
 * 하고 **평생 누적 카운터**를 돌려준다(TikTok `view_count`, Instagram `plays`,
 * Dailymotion `views_total`, Pinterest `lifetime_metrics` …).
 *
 * 스케줄러는 그 값을 그 날짜 행에 그대로 넣었고 화면은 그것을 기간 `SUM` 했다.
 * 30일 창이면 조회수가 **약 30배**가 된다. 크리에이터가 첫 화면에서 보는 숫자다.
 *
 * ## 왜 백필까지 막는가
 *
 * 백필은 "그 날짜의 값을 물어볼 수 있다" 를 전제한다. 누적 플랫폼에는 그런 질의가 없다 —
 * 무엇을 물어도 지금 이 순간의 누적값이 온다. 그래서 예전 코드는 **오늘의 값을 빈 과거
 * 날짜마다 복사**했다. 차분을 도입해도 백필이 남아 있으면 같은 값이 여러 날짜에 들어가
 * 문제가 되풀이된다.
 */
class AnalyticsCumulativeDeltaTest {

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

    /**
     * @param gapDays 최근 며칠을 비워 둘지. 백필이 도는 조건을 만든다.
     */
    private fun stubChannel(platform: Platform, gapDays: Long = 0) {
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

        val today = LocalDate.now()
        every { analyticsRepository.findLatestDateByVideoUploadId(uploadId) } returns
            today.minusDays(gapDays + 1)
        // gapDays 만큼 최근 날짜를 비워 둔다. 비면 datesToSync 가 그 날짜들을 돌려준다.
        every { analyticsRepository.findByVideoUploadIdAndDateRange(uploadId, any(), any()) } answers {
            val from = secondArg<LocalDate>()
            val to = thirdArg<LocalDate>()
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { !it.isAfter(to) }
                .filter { it.isBefore(today.minusDays(gapDays)) }
                .map { AnalyticsDaily(videoUploadId = uploadId, date = it) }
                .toList()
        }
        every {
            platformClientPort.getVideoAnalytics(any(), any(), any(), any(), any())
        } returns PlatformAnalyticsResult(
            views = 1_000, likes = 80, comments = 30, shares = 12,
            watchTimeSeconds = 0, subscriberGained = 0,
            impressions = 0, avgViewDurationSeconds = 0,
        )
    }

    private fun capturedRows(): List<AnalyticsDaily> {
        val rows = mutableListOf<AnalyticsDaily>()
        every { analyticsRepository.upsert(capture(rows)) } answers { firstArg() }
        return rows
    }

    // ---------------------------------------------------------------- 누적 플랫폼

    /**
     * **핵심 회귀.** 기준선이 있으면 차분만 저장한다.
     *
     * 이전 관측이 900 이고 지금이 1,000 이면 그 날 늘어난 것은 100 이다. 1,000 을
     * 저장하면 다음 날도 1,000 이 쌓여 합계가 터진다.
     */
    @Test
    @DisplayName("누적 플랫폼은 직전 스냅샷과의 차분을 저장한다")
    fun cumulativePlatformStoresDelta() {
        stubChannel(Platform.TIKTOK)
        every { analyticsRepository.findLatestTotalsBefore(uploadId, any()) } returns
            EngagementTotals(views = 900, likes = 70, comments = 25, shares = 10)

        val rows = capturedRows()
        scheduler().syncAnalytics()

        val row = rows.single()
        assertEquals(100, row.views, "조회수 증분이 틀렸다 — 1000 - 900 = 100")
        assertEquals(10, row.likes)
        assertEquals(5, row.commentsCount)
        assertEquals(2, row.shares)
        assertEquals(
            EngagementBasis.INCREMENTAL, row.engagementBasis,
            "차분을 냈으면 합산 가능해야 한다",
        )
    }

    /** 다음 주기의 기준선이 되므로 원본 누적값도 함께 남아야 한다. */
    @Test
    @DisplayName("원본 누적값을 스냅샷 칸에 보존한다")
    fun cumulativePlatformKeepsSnapshot() {
        stubChannel(Platform.TIKTOK)
        every { analyticsRepository.findLatestTotalsBefore(uploadId, any()) } returns
            EngagementTotals(views = 900, likes = 70, comments = 25, shares = 10)

        val rows = capturedRows()
        scheduler().syncAnalytics()

        val row = rows.single()
        assertEquals(1_000L, row.viewsTotal, "스냅샷이 없으면 다음 주기의 차분을 낼 수 없다")
        assertEquals(80L, row.likesTotal)
        assertEquals(30L, row.commentsTotal)
        assertEquals(12L, row.sharesTotal)
    }

    /**
     * 첫 관측은 비교 대상이 없다. 0 을 실측처럼 두면 "그 날 조회수가 0 이었다" 가 된다.
     */
    @Test
    @DisplayName("기준선이 없는 첫 관측은 BASELINE 이라 합산에서 빠진다")
    fun firstObservationIsBaseline() {
        stubChannel(Platform.TIKTOK)
        every { analyticsRepository.findLatestTotalsBefore(uploadId, any()) } returns null

        val rows = capturedRows()
        scheduler().syncAnalytics()

        val row = rows.single()
        assertEquals(EngagementBasis.BASELINE, row.engagementBasis)
        assertTrue(!row.engagementBasis.summable, "BASELINE 은 합산 대상이 아니다")
        assertEquals(1_000L, row.viewsTotal, "기준선을 세우려면 스냅샷은 남아야 한다")
    }

    /**
     * 댓글·좋아요 삭제나 스팸 정정으로 누적이 줄 수 있다. 음수를 그대로 저장하면
     * `CHECK (views >= 0)` 에 걸려 그 영상의 동기화가 영구히 실패한다.
     */
    @Test
    @DisplayName("누적이 줄어도 음수를 저장하지 않는다")
    fun decreasingCounterDoesNotProduceNegative() {
        stubChannel(Platform.TIKTOK)
        every { analyticsRepository.findLatestTotalsBefore(uploadId, any()) } returns
            EngagementTotals(views = 1_500, likes = 200, comments = 90, shares = 40)

        val rows = capturedRows()
        scheduler().syncAnalytics()

        val row = rows.single()
        assertEquals(0, row.views, "감소는 0 으로 막는다 — 음수는 CHECK 제약에 걸린다")
        assertEquals(0, row.likes)
        assertEquals(0, row.commentsCount)
        assertEquals(0, row.shares)
    }

    /**
     * **백필 금지.** 과거 날짜의 누적값은 알 수 없다. 오늘 값을 복사하면 같은 숫자가
     * 여러 날짜에 들어가 합계가 다시 부푼다.
     */
    @Test
    @DisplayName("누적 플랫폼은 빈 과거 날짜를 백필하지 않는다")
    fun cumulativePlatformDoesNotBackfill() {
        stubChannel(Platform.TIKTOK, gapDays = 5)
        every { analyticsRepository.findLatestTotalsBefore(uploadId, any()) } returns
            EngagementTotals(views = 900, likes = 70, comments = 25, shares = 10)

        val rows = capturedRows()
        scheduler().syncAnalytics()

        assertEquals(
            listOf(LocalDate.now()), rows.map { it.date },
            "오늘 하루만 기록해야 한다 — 과거 5일을 채우면 같은 값이 6번 들어간다",
        )
    }

    // ---------------------------------------------------------------- 기간값 플랫폼

    /**
     * YouTube 는 Analytics API 에 기간을 넘기고 그 기간의 값을 받는다. 차분하면 안 된다.
     */
    @Test
    @DisplayName("YouTube 는 받은 값을 그대로 저장하고 차분하지 않는다")
    fun incrementalPlatformStoresValueAsIs() {
        stubChannel(Platform.YOUTUBE)

        val rows = capturedRows()
        scheduler().syncAnalytics()

        val row = rows.first()
        assertEquals(1_000, row.views, "기간값을 차분하면 실제 조회수가 사라진다")
        assertEquals(80, row.likes)
        assertEquals(EngagementBasis.INCREMENTAL, row.engagementBasis)
        assertNull(row.viewsTotal, "기간값 플랫폼은 누적 스냅샷을 갖지 않는다")

        // 차분을 시도조차 하지 않아야 한다. 불필요한 조회는 그 자체로 계약 위반이다.
        verify(exactly = 0) { analyticsRepository.findLatestTotalsBefore(any(), any()) }
    }

    /** 기간값 플랫폼의 백필은 그대로 살아 있어야 한다 — 빠진 날짜를 실제로 물어볼 수 있다. */
    @Test
    @DisplayName("YouTube 는 빈 과거 날짜를 계속 백필한다")
    fun incrementalPlatformStillBackfills() {
        stubChannel(Platform.YOUTUBE, gapDays = 3)

        val rows = capturedRows()
        scheduler().syncAnalytics()

        assertTrue(
            rows.size > 1,
            "백필이 사라졌다 — 누적 플랫폼 수정이 YouTube 까지 막았다. 기록된 날짜: ${rows.map { it.date }}",
        )
    }
}
