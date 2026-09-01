package com.ongo.application.analytics

import com.ongo.application.analytics.dto.CohortGroupResponse
import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코호트의 누적 곡선·평균 조회수가 **조회수를 실제로 보고하는 행으로만** 만들어지는지
 * 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `buildCohortGroup` 은 `allAnalytics` 의 모든 `AnalyticsDaily.views` 를 그대로 더했다.
 * `AnalyticsDaily` 에는 플랫폼이 없어서, `TumblrClient.kt:141` 의 `total_notes`
 * (좋아요+리블로그+답글 총합)가 조회수로 섞였다.
 *
 * 수집하지 않는 플랫폼의 하드코딩 0 과 다르다. **다른 뜻의 큰 숫자**라 합계가 부풀고,
 * `getCohortAnalysis` 가 `sortedByDescending { it.avgViews }` 로 정렬하기 때문에 그
 * 코호트가 **성과 1 위 카테고리**로 화면 맨 위에 올라간다.
 *
 * ## 여기서 고정하는 것
 *
 * - unsupported-only 코호트 → `avgViews = null` + 빈 곡선 + 명시적 사유
 * - 혼합 코호트 → **지원 행만** 합산, 분모도 잴 수 있는 영상만
 * - 지원 플랫폼의 실측 0 → `0`
 * - 수집하는 플랫폼의 조회수는 그대로 산다(과도한 차단 회귀 방지)
 */
class CohortPlatformMixingTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = CohortAnalysisUseCase(
        analyticsRepository, videoRepository, videoUploadRepository,
    )

    private val userId = 7L
    private val created: LocalDate = LocalDate.now().minusDays(40)

    private fun video(id: Long) = Video(
        id = id,
        userId = userId,
        title = "영상 $id",
        category = "브이로그",
        createdAt = created.atStartOfDay(),
    )

    /** 게시 당일 한 건만 둔다 — 모든 마일스톤 구간에 동일하게 잡힌다. */
    private fun row(uploadId: Long, views: Int) =
        AnalyticsDaily(videoUploadId = uploadId, date = created, views = views)

    /**
     * @param uploads 영상 id → (업로드 id, 플랫폼)
     * @param rows 업로드 id → 그 업로드의 집계 행
     */
    private fun cohort(
        uploads: Map<Long, List<Pair<Long, Platform>>>,
        rows: List<AnalyticsDaily>,
    ): CohortGroupResponse {
        every { videoRepository.findByUserId(userId, any(), any()) } returns uploads.keys.map { video(it) }
        every { videoUploadRepository.findByVideoIds(any()) } returns uploads.mapValues { (videoId, list) ->
            list.map { (uploadId, platform) ->
                VideoUpload(id = uploadId, videoId = videoId, platform = platform, channelId = 1L)
            }
        }
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } answers {
            // 저장소는 요청한 업로드 id 의 행만 돌려준다. 그 계약을 흉내 내야
            // "쿼리 단계에서 걸러졌다" 는 사실이 테스트에 반영된다.
            val requested = firstArg<List<Long>>().toSet()
            rows.filter { it.videoUploadId in requested }.groupBy { it.videoUploadId }
        }

        return useCase.getCohortAnalysis(userId, CohortGroupBy.CATEGORY, null, null).cohorts.single()
    }

    // ══ unsupported-only ════════════════════════════════════════════════════

    /** **이 케이스가 노트 총합 90 만을 "평균 조회수" 로 올리던 자리다.** */
    @Test
    @DisplayName("Tumblr에만 게시한 코호트는 평균 조회수를 만들지 않는다")
    fun tumblrOnlyCohortHasNoAverage() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.TUMBLR)),
            rows = listOf(row(101L, views = 900_000)),
        )

        assertNull(group.avgViews, "노트 총합을 평균 조회수로 내보냈다")
        assertTrue(group.cumulativeViewCurve.isEmpty(), "재지 않은 곡선을 그렸다")
        assertEquals(CohortAnalysisUseCase.COHORT_VIEWS_NOT_COLLECTED, group.unavailableReason)
        // 코호트 소속 영상 수는 측정 여부와 무관한 사실이다.
        assertEquals(1, group.videoCount)
    }

    /**
     * **Pinterest 는 조회수를 준다** — 지워버리면 안 된다.
     *
     * `PinterestClient.kt:157` 은 `views = metrics["IMPRESSION"]` 이고, 계약도 Pinterest 의
     * 미수집 지표를 `LIKES`·`COMMENTS`·`SHARES` 로만 선언한다
     * (`PlatformMetricAvailability.kt:129`). 조회수까지 같이 끊으면 이번 수정이 실측을
     * 버리는 회귀가 된다. 섞이면 안 되는 것은 Tumblr 의 `total_notes` 쪽이다.
     */
    @Test
    @DisplayName("Pinterest 조회수는 계약대로 살아 있다")
    fun pinterestViewsSurvive() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.PINTEREST)),
            rows = listOf(row(101L, views = 12_345)),
        )

        assertEquals(12_345L, group.avgViews, "수집하는 지표까지 함께 끊었다")
        assertTrue(group.cumulativeViewCurve.isNotEmpty())
        assertNull(group.unavailableReason)
    }

    /**
     * 미수집과 **실제 0** 은 다른 사유여야 한다. 같은 문구로 뭉개면 화면이 둘을 구분 못 한다.
     */
    @Test
    @DisplayName("미수집 사유는 실측 0 사유와 다르다")
    fun notCollectedReasonDiffersFromZeroReason() {
        val notCollected = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.TUMBLR)),
            rows = listOf(row(101L, views = 900_000)),
        ).unavailableReason
        val measuredZero = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.YOUTUBE)),
            rows = listOf(row(101L, views = 0)),
        ).unavailableReason

        assertEquals(CohortAnalysisUseCase.COHORT_VIEWS_NOT_COLLECTED, notCollected)
        assertEquals(CohortAnalysisUseCase.COHORT_NO_VIEWS, measuredZero)
        assertTrue(notCollected != measuredZero, "두 상태가 같은 문구로 나갔다")
    }

    // ══ 혼합 플랫폼 ═════════════════════════════════════════════════════════

    /** **여기가 이번 수정의 핵심이다.** 같은 영상을 YouTube 와 Tumblr 에 함께 올린 경우. */
    @Test
    @DisplayName("한 영상의 Tumblr 업로드는 조회수 합계에 섞이지 않는다")
    fun tumblrUploadOfSameVideoIsExcluded() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.YOUTUBE, 102L to Platform.TUMBLR)),
            rows = listOf(row(101L, views = 500), row(102L, views = 900_000)),
        )

        assertEquals(500L, group.avgViews, "Tumblr 노트 총합이 조회수에 섞였다")
        assertTrue(group.cumulativeViewCurve.all { it.value == 500L })
        assertNull(group.unavailableReason)
    }

    /**
     * 평균의 **분모도 같은 관측에서 나와야 한다.**
     *
     * Tumblr 에만 올린 영상을 분모에 넣으면 "그 영상은 조회수 0 이었다" 고 주장하는 것과
     * 같다. 재지 않았을 뿐인데 평균이 절반으로 떨어진다.
     */
    @Test
    @DisplayName("잴 수 없는 영상은 평균의 분모에 넣지 않는다")
    fun unmeasurableVideosAreNotInTheDenominator() {
        val group = cohort(
            uploads = mapOf(
                1L to listOf(101L to Platform.YOUTUBE),
                2L to listOf(102L to Platform.TUMBLR),
            ),
            rows = listOf(row(101L, views = 1_000), row(102L, views = 900_000)),
        )

        // 잰 영상은 1 편뿐이다. 2 로 나누면 500 이 되어 실측을 절반으로 깎는다.
        assertEquals(1_000L, group.avgViews, "재지 않은 영상을 분모에 넣어 평균을 깎았다")
        // 소속 영상 수는 2 편 그대로다.
        assertEquals(2, group.videoCount)
    }

    @Test
    @DisplayName("지원하는 플랫폼끼리는 정상적으로 합산한다")
    fun supportedPlatformsAreSummedTogether() {
        val group = cohort(
            uploads = mapOf(
                1L to listOf(101L to Platform.YOUTUBE, 102L to Platform.TIKTOK, 103L to Platform.TUMBLR),
            ),
            rows = listOf(row(101L, views = 300), row(102L, views = 200), row(103L, views = 900_000)),
        )

        assertEquals(500L, group.avgViews)
        assertTrue(group.cumulativeViewCurve.all { it.value == 500L })
    }

    /** 정규화 기준도 오염되면 안 된다 — 곡선 모양 자체가 달라진다. */
    @Test
    @DisplayName("정규화 기준은 지원 행의 최대값으로 잡는다")
    fun normalizationBaselineUsesSupportedRowsOnly() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.YOUTUBE, 102L to Platform.TUMBLR)),
            rows = listOf(row(101L, views = 400), row(102L, views = 900_000)),
        )

        // 모든 구간이 같은 값이므로 전부 100% 여야 한다. Tumblr 가 섞이면 0.04% 로 눌린다.
        assertTrue(
            group.cumulativeViewCurve.all { it.normalizedPercent == 100.0 },
            "정규화 기준에 노트 총합이 섞였다: ${group.cumulativeViewCurve.map { it.normalizedPercent }}",
        )
    }

    // ══ 실측 0 은 보존 ══════════════════════════════════════════════════════

    /** **과도한 null 처리 회귀를 막는다.** 행을 봤고 합이 0 이면 그 0 은 관측이다. */
    @Test
    @DisplayName("지원 플랫폼의 측정된 0은 0으로 남는다")
    fun measuredZeroStaysZero() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.YOUTUBE)),
            rows = listOf(row(101L, views = 0)),
        )

        assertEquals(0L, group.avgViews, "실측 0 을 미측정으로 감췄다")
        assertTrue(group.cumulativeViewCurve.isNotEmpty(), "측정된 구간을 지웠다")
        assertTrue(group.cumulativeViewCurve.all { it.value == 0L })
        // 정규화 기준은 없다 — 0 으로 나눌 수 없다.
        assertTrue(group.cumulativeViewCurve.all { it.normalizedPercent == null })
        assertEquals(CohortAnalysisUseCase.COHORT_NO_VIEWS, group.unavailableReason)
    }

    /** 혼합 코호트에서도 실측 0 은 살아남아야 한다. */
    @Test
    @DisplayName("혼합 코호트의 측정된 0도 0으로 남는다")
    fun measuredZeroSurvivesMixedPlatforms() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.YOUTUBE, 102L to Platform.TUMBLR)),
            rows = listOf(row(101L, views = 0), row(102L, views = 900_000)),
        )

        assertEquals(0L, group.avgViews, "실측 0 을 미측정으로 감췄다")
        assertEquals(CohortAnalysisUseCase.COHORT_NO_VIEWS, group.unavailableReason)
    }

    /**
     * 지원 플랫폼에 게시했지만 **집계 행이 아직 없는** 상태는 0 이 아니다.
     * 곱해서 총합을 되짚는 소비자에게 `avgViews = 0` 은 "0 회" 라는 관측이 된다.
     *
     * **곡선도 함께 비어야 한다.** 예전에는 `cumulativeMap[day] = 0` 이 무조건 채워져,
     * 행이 하나도 없는데 모든 마일스톤에 0 점이 박힌 **평평한 곡선**이 그려졌다.
     * `avgViews = null` 과 같은 응답 안에서 서로 모순되는 상태였다.
     */
    @Test
    @DisplayName("지원 플랫폼 행이 아직 없으면 평균도 곡선도 비운다")
    fun supportedPlatformWithoutRowsIsNotZero() {
        val group = cohort(
            uploads = mapOf(1L to listOf(101L to Platform.YOUTUBE)),
            rows = emptyList(),
        )

        assertNull(group.avgViews, "수집 전 상태를 평균 0 회로 위장했다")
        assertTrue(
            group.cumulativeViewCurve.isEmpty(),
            "행이 없는데 0 점 곡선을 그렸다: ${group.cumulativeViewCurve.map { it.value }}",
        )
        assertEquals(CohortAnalysisUseCase.COHORT_NO_VIEWS, group.unavailableReason)
        // 미수집(영원히 못 잼)과는 다른 사유여야 한다 — 이쪽은 기다리면 채워진다.
        assertTrue(group.unavailableReason != CohortAnalysisUseCase.COHORT_VIEWS_NOT_COLLECTED)
    }

    /**
     * **잰 영상만 분모에 넣는다.**
     *
     * 둘 다 YouTube 라 플랫폼만 보면 분모가 2 다. 하지만 2 번 영상은 기간 안에 집계된
     * 행이 없어 조회수를 잰 적이 없다. 분모에 넣으면 "0 회였다" 고 주장하는 것과 같아
     * 평균이 절반이 된다.
     */
    @Test
    @DisplayName("지원 영상이라도 행이 없으면 평균의 분모에 넣지 않는다")
    fun supportedVideosWithoutRowsLeaveTheDenominator() {
        val group = cohort(
            uploads = mapOf(
                1L to listOf(101L to Platform.YOUTUBE),
                2L to listOf(102L to Platform.YOUTUBE),
            ),
            rows = listOf(row(101L, views = 1_000)),
        )

        assertEquals(1_000L, group.avgViews, "재지 않은 영상을 분모에 넣어 평균을 깎았다")
        assertEquals(2, group.videoCount)
        assertNull(group.unavailableReason)
    }

    /** 두 영상 모두 관측됐다면 분모는 2 다 — 위 필터가 과하게 걸러내면 안 된다. */
    @Test
    @DisplayName("둘 다 관측된 영상은 둘 다 분모에 넣는다")
    fun observedVideosAllCountTowardTheDenominator() {
        val group = cohort(
            uploads = mapOf(
                1L to listOf(101L to Platform.YOUTUBE),
                2L to listOf(102L to Platform.YOUTUBE),
            ),
            rows = listOf(row(101L, views = 1_000), row(102L, views = 500)),
        )

        // (1,000 + 500) / 2 = 750
        assertEquals(750L, group.avgViews)
    }

    /**
     * **실측 0 행은 관측이다.** 분모에서 빼면 안 된다 — 빼면 다른 영상의 평균만 남아
     * 실제보다 높아진다.
     */
    @Test
    @DisplayName("조회수 0 행도 관측으로 인정해 분모에 넣는다")
    fun measuredZeroRowsCountAsObserved() {
        val group = cohort(
            uploads = mapOf(
                1L to listOf(101L to Platform.YOUTUBE),
                2L to listOf(102L to Platform.YOUTUBE),
            ),
            rows = listOf(row(101L, views = 1_000), row(102L, views = 0)),
        )

        // (1,000 + 0) / 2 = 500. 0 행을 버리면 1,000 이 되어 성과가 부풀려진다.
        assertEquals(500L, group.avgViews, "실측 0 을 관측에서 제외해 평균을 부풀렸다")
        assertEquals(2, group.videoCount)
    }

    // ══ 정렬 계약 ═══════════════════════════════════════════════════════════

    /**
     * `getCohortAnalysis` 는 `avgViews` 로 내림차순 정렬한다. 오염된 코호트가 1 위로
     * 올라가던 것이 이 버그의 실제 피해였다.
     */
    @Test
    @DisplayName("미측정 코호트가 성과 1위로 올라가지 않는다")
    fun unmeasurableCohortDoesNotTopTheRanking() {
        every { videoRepository.findByUserId(userId, any(), any()) } returns listOf(
            video(1L).copy(category = "실측"),
            video(2L).copy(category = "노트총합"),
        )
        every { videoUploadRepository.findByVideoIds(any()) } returns mapOf(
            1L to listOf(VideoUpload(id = 101L, videoId = 1L, platform = Platform.YOUTUBE, channelId = 1L)),
            2L to listOf(VideoUpload(id = 102L, videoId = 2L, platform = Platform.TUMBLR, channelId = 1L)),
        )
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } answers {
            val requested = firstArg<List<Long>>().toSet()
            listOf(row(101L, views = 100), row(102L, views = 900_000))
                .filter { it.videoUploadId in requested }
                .groupBy { it.videoUploadId }
        }

        val cohorts = useCase.getCohortAnalysis(userId, CohortGroupBy.CATEGORY, null, null).cohorts

        assertEquals("실측", cohorts.first().name, "재지 않은 코호트가 1 위로 올라갔다")
        assertEquals(100L, cohorts.first().avgViews)
        assertNull(cohorts.last().avgViews)
    }
}
