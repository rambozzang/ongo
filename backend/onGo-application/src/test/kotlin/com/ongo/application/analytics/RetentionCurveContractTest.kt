package com.ongo.application.analytics

import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 구간별 시청 유지율은 **지어내지 않는다.**
 *
 * 예전에는 모든 영상의 길이를 5분으로 가정하고 지수감쇠 공식으로 21개 점과 이탈 사유를
 * 만들어, 측정된 분석처럼 돌려줬다. 실제로는 영상 길이도, 구간별 유지율도, 이탈 지점도
 * 어떤 플랫폼 어댑터에서 오지 않는다.
 *
 * 이 테스트가 지키는 것: **빈 배열 + available=false + 사유**만 나가고, 어떤 경로에서도
 * 값이 채워진 배열이 나오지 않는다.
 */
class RetentionCurveContractTest {

    private val analyticsRepository = mockk<AnalyticsRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()

    private val useCase = CohortAnalysisUseCase(
        analyticsRepository = analyticsRepository,
        videoRepository = videoRepository,
        videoUploadRepository = videoUploadRepository,
    )

    private val userId = 7L
    private val videoId = 42L

    private fun stubVideo() {
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "원본",
            description = null,
            status = UploadStatus.PUBLISHED,
        )
    }

    @Test
    @DisplayName("유지율 곡선은 항상 비어 있고 사유를 함께 준다")
    fun neverFabricatesACurve() {
        stubVideo()

        val result = useCase.getRetentionCurve(userId, videoId)

        assertEquals(videoId, result.videoId)
        assertTrue(result.retentionPoints.isEmpty(), "유지율 점을 지어냈습니다")
        assertTrue(result.avgRetention.isEmpty(), "채널 평균 곡선을 지어냈습니다")
        assertTrue(result.dropOffPoints.isEmpty(), "이탈 지점을 지어냈습니다")
        assertFalse(result.available)
        assertTrue(result.unavailableReason!!.contains("제공하지 않아"), result.unavailableReason!!)
    }

    /**
     * **분석 데이터가 있어도 마찬가지다.** 예전 구현은 `analytics_daily` 가 있으면
     * 거기서 곡선을 파생시켰다 — 그 데이터에는 구간별 유지율이 없는데도.
     */
    @Test
    @DisplayName("분석 데이터가 쌓여 있어도 곡선을 만들지 않는다")
    fun doesNotDeriveFromDailyAnalytics() {
        stubVideo()
        every { videoUploadRepository.findByVideoId(videoId) } returns listOf(
            VideoUpload(id = 1L, videoId = videoId, platform = com.ongo.common.enums.Platform.YOUTUBE),
        )
        every { analyticsRepository.findByVideoUploadIds(any()) } returns listOf(
            AnalyticsDaily(
                videoUploadId = 1L,
                date = LocalDate.of(2026, 8, 20),
                views = 5_000,
                watchTimeSeconds = 900_000,
            ),
        )

        val result = useCase.getRetentionCurve(userId, videoId)

        assertTrue(result.retentionPoints.isEmpty())
        assertFalse(result.available)
        // 파생 근거를 읽지도 않는다. 읽으면 언젠가 다시 곡선을 만들게 된다.
        verify(exactly = 0) { analyticsRepository.findByVideoUploadIds(any()) }
        verify(exactly = 0) { analyticsRepository.findAllByUserId(any()) }
    }

    /** 소유권 검사는 유지한다 — 남의 영상 ID 로 존재를 떠보는 경로를 열지 않는다. */
    @Test
    @DisplayName("다른 사용자의 영상은 거절한다")
    fun rejectsOtherUsersVideo() {
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = 999L,
            title = "남의 영상",
            description = null,
            status = UploadStatus.PUBLISHED,
        )

        assertFailsWith<ForbiddenException> { useCase.getRetentionCurve(userId, videoId) }
    }

    /** 하위 호환: 새 필드는 기본값이 있어야 기존 생성자가 깨지지 않는다. */
    @Test
    @DisplayName("응답 새 필드는 미측정이 기본값이다")
    fun newFieldsDefaultToUnavailable() {
        val legacy = com.ongo.application.analytics.dto.RetentionCurveResponse(
            videoId = 1L,
            retentionPoints = emptyList(),
            avgRetention = emptyList(),
            dropOffPoints = emptyList(),
        )

        assertFalse(legacy.available)
        assertEquals(null, legacy.unavailableReason)
    }
}
