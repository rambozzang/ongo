package com.ongo.application.analytics

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코호트 유지 곡선이 **정규화할 기준 없이 0% 를 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * val maxViews = totalViews.coerceAtLeast(1)
 * normalizedPercent = Math.round(views.toDouble() / maxViews * 10000) / 100.0
 * ```
 *
 * 조회가 전혀 없는 코호트에서는 분모가 `1` 이 되어 모든 구간이 `0 / 1 * 100 = 0.0` 이
 * 됐다. 화면은 그것을 **평평한 0% 유지 곡선**으로 그렸다 — 재지 않았을 뿐인데
 * "끝까지 아무도 안 봤다" 는 관측이 된다.
 *
 * 기준이 있는 상태의 `0.0` 은 "그 구간까지 조회가 없었다" 는 실제 관측이므로 유지한다.
 */
class CohortNormalizationTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = CohortAnalysisUseCase(
        analyticsRepository, videoRepository, videoUploadRepository,
    )

    private val userId = 7L
    private val created = LocalDate.now().minusDays(40)

    private fun video(id: Long, category: String) = Video(
        id = id,
        userId = userId,
        title = "영상 $id",
        category = category,
        createdAt = created.atStartOfDay(),
    )

    /** 게시 이후 며칠간의 집계. `views` 가 0 이면 조회가 없었다는 뜻이다. */
    private fun rows(uploadId: Long, views: Int) = (0..30).map { offset ->
        AnalyticsDaily(
            videoUploadId = uploadId,
            date = created.plusDays(offset.toLong()),
            views = views,
        )
    }

    private fun given(views: Int) {
        every { videoRepository.findByUserId(userId, any(), any()) } returns listOf(video(1L, "브이로그"))
        every { videoUploadRepository.findByVideoIds(any()) } returns mapOf(
            1L to listOf(VideoUpload(id = 101L, videoId = 1L, platform = Platform.YOUTUBE, channelId = 1L)),
        )
        every { analyticsRepository.findByVideoUploadIdsAndDateRange(any(), any(), any()) } returns
            mapOf(101L to rows(101L, views))
    }

    private fun cohort() = useCase
        .getCohortAnalysis(userId, CohortGroupBy.CATEGORY, null, null)
        .cohorts
        .single()

    // ── 정규화 기준이 없을 때 ───────────────────────────────────────────────

    /** **이 케이스가 평평한 0% 곡선을 그리던 자리다.** */
    @Test
    @DisplayName("조회가 하나도 없으면 유지율을 0%로 그리지 않는다")
    fun noViewsProducesNoNormalizedCurve() {
        given(views = 0)

        val group = cohort()

        assertTrue(
            group.cumulativeViewCurve.all { it.normalizedPercent == null },
            "정규화 기준이 없는데 비율을 만들었다: ${group.cumulativeViewCurve.map { it.normalizedPercent }}",
        )
        assertEquals(CohortAnalysisUseCase.COHORT_NO_VIEWS, group.unavailableReason)
    }

    /** 누적 조회수 자체는 관측이다 — 0 도 그대로 남는다. */
    @Test
    @DisplayName("누적 조회수 0은 관측값으로 남긴다")
    fun cumulativeValueKeepsMeasuredZero() {
        given(views = 0)

        assertTrue(cohort().cumulativeViewCurve.all { it.value == 0L })
    }

    // ── 정규화 기준이 있을 때 ───────────────────────────────────────────────

    @Test
    @DisplayName("조회가 있으면 유지 곡선을 정규화한다")
    fun measuredViewsProduceANormalizedCurve() {
        given(views = 100)

        val group = cohort()

        assertTrue(
            group.cumulativeViewCurve.all { it.normalizedPercent != null },
            "측정됐는데 곡선을 비웠다",
        )
        // 마지막 구간이 최대 누적이므로 100% 다.
        assertEquals(100.0, group.cumulativeViewCurve.last().normalizedPercent)
        assertNull(group.unavailableReason)
    }

    @Test
    @DisplayName("조회가 있으면 평균 조회수를 낸다")
    fun measuredViewsProduceAnAverage() {
        given(views = 100)

        assertNotNull(cohort().avgViews)
    }

    /**
     * 곡선은 누적이라 앞 구간이 뒤 구간보다 작다. 그 차이는 실제 관측이므로
     * 정규화 비율도 단조 증가해야 한다.
     */
    @Test
    @DisplayName("누적 곡선의 정규화 비율은 구간이 갈수록 커진다")
    fun normalizedCurveGrowsWithEachMilestone() {
        given(views = 100)

        val percents = cohort().cumulativeViewCurve.mapNotNull { it.normalizedPercent }

        assertTrue(percents.size >= 2, "구간이 부족하다: $percents")
        assertTrue(
            percents.zipWithNext().all { (a, b) -> b >= a },
            "누적인데 비율이 줄었다: $percents",
        )
    }

    // ── 영상이 없는 코호트 ───────────────────────────────────────────────────

    @Test
    @DisplayName("영상이 없으면 코호트 자체를 만들지 않는다")
    fun noVideosProduceNoCohort() {
        every { videoRepository.findByUserId(userId, any(), any()) } returns emptyList()

        val response = useCase.getCohortAnalysis(userId, CohortGroupBy.CATEGORY, null, null)

        assertTrue(response.cohorts.isEmpty(), "빈 코호트를 지어냈다")
    }

    /** 사유는 숫자가 아니라 문장이어야 한다. */
    @Test
    @DisplayName("측정 불가 사유에 숫자가 들어가지 않는다")
    fun reasonsAreSentencesNotNumbers() {
        listOf(
            CohortAnalysisUseCase.COHORT_NO_VIDEOS,
            CohortAnalysisUseCase.COHORT_NO_VIEWS,
            CohortAnalysisUseCase.COHORT_VIEWS_NOT_COLLECTED,
        ).forEach {
            assertTrue(it.isNotBlank())
            assertTrue(!Regex("[0-9]").containsMatchIn(it), "사유에 숫자가 있다: $it")
        }
    }
}
