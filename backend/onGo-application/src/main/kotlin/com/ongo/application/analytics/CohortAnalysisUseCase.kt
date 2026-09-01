package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CohortGroupBy {
    CATEGORY,
    TAG,
    PLATFORM,
    UPLOAD_MONTH,
}

@Service
class CohortAnalysisUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {

    companion object {
        /** 이 코호트에 영상이 하나도 없다 — 평균을 낼 대상이 없다. */
        const val COHORT_NO_VIDEOS = "이 그룹에 속한 영상이 없습니다"

        /** 영상은 있으나 조회가 하나도 측정되지 않아 곡선을 정규화할 기준이 없다. */
        const val COHORT_NO_VIEWS = "조회수가 집계되지 않아 유지 곡선을 계산할 수 없습니다"

        /**
         * 이 코호트의 영상이 **조회수를 보고하지 않는 플랫폼에만** 게시돼 있다.
         *
         * [COHORT_NO_VIEWS] 와 구분한다 — 저쪽은 수집 대상이지만 아직 집계 전이거나
         * 실제로 0 이고, 이쪽은 물어볼 곳 자체가 없어 기다려도 채워지지 않는다.
         * 예를 들어 Tumblr 는 `total_notes`(노트 총합)만 주고 조회수를 주지 않는다.
         */
        const val COHORT_VIEWS_NOT_COLLECTED = "조회수를 수집하는 플랫폼에 게시된 기록이 없습니다"

        /**
         * 구간별 유지율을 제공할 수 없는 이유. **"데이터가 아직 없음"과 구분되는 문구여야
         * 한다** — 기다리면 생기는 것이 아니라 연동 자체가 없는 상태다.
         */
        const val RETENTION_UNAVAILABLE_REASON =
            "구간별 시청 유지율은 현재 플랫폼 분석 연동에서 제공하지 않아 표시할 수 없습니다."
    }

    @Cacheable(value = ["cohortAnalysis"], key = "#userId + '-' + #groupBy + '-' + #from + '-' + #to")
    fun getCohortAnalysis(
        userId: Long,
        groupBy: CohortGroupBy,
        from: LocalDate?,
        to: LocalDate?,
    ): CohortAnalysisResponse {
        val dateFrom = from ?: LocalDate.now().minusDays(90)
        val dateTo = to ?: LocalDate.now()

        val videos = videoRepository.findByUserId(userId, page = 0, size = 1000)
        if (videos.isEmpty()) {
            return CohortAnalysisResponse(
                groupBy = groupBy.name,
                cohorts = emptyList(),
                dateRange = DateRangeInfo(dateFrom.toString(), dateTo.toString()),
            )
        }

        // Group videos by criteria
        val groupedVideos = groupVideos(videos, groupBy)

        val cohorts = groupedVideos.map { (groupName, groupVideos) ->
            buildCohortGroup(groupName, groupVideos, dateFrom, dateTo)
        }.sortedByDescending { it.avgViews }

        return CohortAnalysisResponse(
            groupBy = groupBy.name,
            cohorts = cohorts,
            dateRange = DateRangeInfo(dateFrom.toString(), dateTo.toString()),
        )
    }

    /**
     * 구간별 시청 유지율. **지금은 제공할 수 없다.**
     *
     * ## 왜 빈 응답인가
     *
     * 예전에는 곡선을 **지어냈다.** 모든 영상의 길이를 5분으로 가정하고, 평균 시청시간을
     * 지수감쇠 공식에 넣어 21개 점을 만들었다. 이탈 사유 문구까지 그 가짜 곡선에서
     * 파생시켰다. 화면에는 측정된 분석처럼 보였지만 어떤 수치도 관측된 값이 아니었다.
     * 그 계산은 전부 삭제했다 — 이름만 바꿔 남겨두면 언제든 다시 배선된다.
     *
     * 실제로 없는 것이 세 가지다.
     *
     * 1. **영상 길이** — `Video` 에 duration 이 없다. x축 자체를 알 수 없다.
     * 2. **구간별 유지율** — 어떤 플랫폼 어댑터도 제공하지 않는다.
     *    `PlatformAnalyticsResult` 는 조회수·좋아요·댓글·공유·시청시간·구독자·노출수·
     *    평균시청시간까지이고, `analytics_daily` 에도 해당 컬럼이 없다.
     * 3. **이탈 지점** — 1·2 가 없으므로 파생될 수 없다.
     *
     * 평균 시청시간(`watchTimeSeconds`)은 실측이지만, 그것으로 구간별 곡선을 만드는 것은
     * 측정이 아니라 추정이다. 추정치를 측정값 자리에 놓지 않는다.
     *
     * 소유권 검사는 유지한다 — 남의 영상 ID 로 존재 여부를 떠보는 경로를 열지 않는다.
     */
    fun getRetentionCurve(userId: Long, videoId: Long): RetentionCurveResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        return RetentionCurveResponse(
            videoId = videoId,
            retentionPoints = emptyList(),
            avgRetention = emptyList(),
            dropOffPoints = emptyList(),
            available = false,
            unavailableReason = RETENTION_UNAVAILABLE_REASON,
        )
    }

    private fun groupVideos(videos: List<Video>, groupBy: CohortGroupBy): Map<String, List<Video>> {
        return when (groupBy) {
            CohortGroupBy.CATEGORY -> videos.groupBy { it.category ?: "미분류" }
            CohortGroupBy.TAG -> {
                // Group by most common tags (each video can appear in multiple groups)
                val tagGroups = mutableMapOf<String, MutableList<Video>>()
                for (video in videos) {
                    val tags = video.tags
                    if (tags.isEmpty()) {
                        tagGroups.getOrPut("태그 없음") { mutableListOf() }.add(video)
                    } else {
                        for (tag in tags.take(3)) { // limit to top 3 tags per video
                            tagGroups.getOrPut(tag) { mutableListOf() }.add(video)
                        }
                    }
                }
                // Keep only groups with at least 2 videos
                tagGroups.filter { it.value.size >= 2 }
                    .entries
                    .sortedByDescending { it.value.size }
                    .take(10)
                    .associate { it.key to it.value }
            }
            CohortGroupBy.PLATFORM -> {
                // Batch fetch all uploads for all videos (eliminates N+1)
                val videoIds = videos.mapNotNull { it.id }
                val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)
                val result = mutableMapOf<String, MutableList<Video>>()
                for (video in videos) {
                    val uploads = uploadsByVideoId[video.id!!] ?: emptyList()
                    for (upload in uploads) {
                        result.getOrPut(upload.platform.name) { mutableListOf() }.add(video)
                    }
                }
                result
            }
            CohortGroupBy.UPLOAD_MONTH -> {
                videos.groupBy { video ->
                    val created = video.createdAt
                    if (created != null) "${created.year}-${created.monthValue.toString().padStart(2, '0')}"
                    else "날짜 없음"
                }
            }
        }
    }

    private fun buildCohortGroup(
        name: String,
        videos: List<Video>,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): CohortGroupResponse {
        val milestones = listOf(1, 3, 7, 14, 30, 60, 90)
        val cumulativeMap = mutableMapOf<Int, Long>()

        // Batch fetch all uploads for this cohort group (eliminates N+1)
        val videoIds = videos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        /*
         * **조회수를 실제로 보고하는 업로드만 센다.**
         *
         * `AnalyticsDaily` 에는 플랫폼이 없어서 예전에는 모든 행의 `views` 를 그대로
         * 더했다. 그래서 `TumblrClient.kt:141` 의 `total_notes`(좋아요+리블로그+답글 총합)가
         * 누적 조회수 곡선과 평균 조회수에 섞였다. 수집하지 않는 플랫폼의 하드코딩 0 과
         * 달리 **다른 뜻의 큰 숫자**라, 그 코호트가 성과 1 위로 정렬되기까지 한다.
         *
         * 행이 아니라 **업로드**로 판정한다 — "조회수를 주는 플랫폼이 없다"(영원히 못
         * 잰다)와 "아직 수집 전이다"(곧 채워진다)는 다른 상태이고, 행만 보면 구분되지 않는다.
         */
        val rowPlatforms = AnalyticsRowPlatforms.of(uploadsByVideoId.values.flatten())
        val viewReportingUploads = uploadsByVideoId.mapValues { (_, uploads) ->
            uploads.filter {
                PlatformMetricAvailability.isAvailable(it.platform.name, PlatformMetricAvailability.VIEWS)
            }
        }

        // Collect all upload IDs for batch analytics query
        val allUploadIds = viewReportingUploads.values.flatten().mapNotNull { it.id }

        // Batch fetch analytics for the full date range (eliminates milestone×video N+1)
        val allAnalytics = if (allUploadIds.isNotEmpty()) {
            analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, dateFrom, dateTo)
        } else {
            emptyMap()
        }

        /**
         * 평균의 분모가 될 영상들. **분자와 같은 관측에서 나와야 한다.**
         *
         * 게시 플랫폼만 보고 분모에 넣으면 안 된다. YouTube 에 올렸어도 기간 안에 집계된
         * 행이 없으면 그 영상의 조회수는 **재지 않은 것**이고, 분모에만 넣으면 "그 영상은
         * 0 회였다" 고 주장하는 셈이라 평균이 인위적으로 낮아진다.
         *
         * 행을 하나라도 본 영상만 넣는다. 그 행의 조회수가 0 이어도 **관측은 관측이므로**
         * 분모에 들어가고 실측 0 이 보존된다.
         */
        val observedVideoIds = mutableSetOf<Long>()

        for (video in videos) {
            val videoCreated = video.createdAt?.toLocalDate() ?: continue
            val uploadIds = viewReportingUploads[video.id!!]?.mapNotNull { it.id } ?: continue
            if (uploadIds.isEmpty()) continue

            for (day in milestones) {
                val endDate = videoCreated.plusDays(day.toLong())
                if (endDate.isAfter(dateTo)) continue

                // Filter pre-fetched analytics in memory by upload IDs and date range
                var views = 0L
                var observedInWindow = false
                for (uploadId in uploadIds) {
                    val windowRows = (allAnalytics[uploadId] ?: emptyList())
                        .filter { !it.date.isBefore(videoCreated) && !it.date.isAfter(endDate) }
                        // 업로드 id 로 이미 걸렀지만, 행 단위로도 한 번 더 확인한다.
                        .filter { rowPlatforms.reports(it, PlatformMetricAvailability.VIEWS) }
                    if (windowRows.isNotEmpty()) observedInWindow = true
                    views += windowRows.sumOf { it.views.toLong() }
                }

                /*
                 * **이 구간에 잰 행이 없으면 점을 찍지 않는다.**
                 *
                 * 예전에는 여기서 `cumulativeMap[day] = 0` 이 항상 채워져, 행이 하나도
                 * 없는 코호트도 모든 마일스톤에 0 점이 박힌 곡선을 그렸다. 화면에는
                 * **평평한 0 곡선**이 관측처럼 보였다. `views == 0` 자체는 판단 근거가
                 * 아니다 — 잰 0 인지 안 잰 0 인지는 행의 존재로만 알 수 있다.
                 */
                if (!observedInWindow) continue

                observedVideoIds.add(video.id!!)
                cumulativeMap[day] = (cumulativeMap[day] ?: 0) + views
            }
        }

        // 게시 자체가 조회수 미지원 플랫폼뿐인가(영원히 못 잼) — 아직 수집 전과 구분한다.
        val viewsCollectable = viewReportingUploads.values.any { it.isNotEmpty() }
        val totalViews = cumulativeMap.values.maxOrNull() ?: 0

        /*
         * **정규화할 기준이 없으면 비율을 만들지 않는다.**
         *
         * 예전에는 `totalViews.coerceAtLeast(1)` 로 분모를 1 로 세웠다. 조회가 전혀 없는
         * 코호트에서는 모든 구간이 `0 / 1 * 100 = 0.0` 이 되어, 화면이 **평평한 0% 유지
         * 곡선**을 그렸다 — 재지 않았을 뿐인데 관측처럼 보인다.
         *
         * 기준이 있을 때의 `0.0` 은 "그 구간까지 조회가 없었다" 는 실제 관측이므로 그대로 둔다.
         */
        val normalizable = observedVideoIds.isNotEmpty() && totalViews > 0

        // `cumulativeMap` 에는 **행을 실제로 본 마일스톤만** 들어 있다(위 `observedInWindow` 참고).
        // 그래서 이 필터가 곧 "그 구간을 쟀는가" 판정이고, 못 잰 구간에는 점이 찍히지 않는다.
        val curve = milestones
            .filter { cumulativeMap.containsKey(it) }
            .map { day ->
                val views = cumulativeMap[day] ?: 0
                DataPoint(
                    day = day,
                    value = views,
                    normalizedPercent = if (normalizable) {
                        Math.round(views.toDouble() / totalViews * 10000) / 100.0
                    } else {
                        null
                    },
                )
            }

        return CohortGroupResponse(
            name = name,
            // 코호트 **소속** 영상 수다. 측정 가능 여부와 무관한 사실이므로 그대로 둔다.
            videoCount = videos.size,
            /*
             * 나눌 대상도, 잰 값도 있어야 평균이 성립한다. 0 은 "평균 0회" 라는 관측이 되므로
             * 재지 않은 자리에 놓지 않는다. 반대로 행을 봤고 합이 0 이면 그 0 은 실측이다.
             */
            avgViews = if (observedVideoIds.isNotEmpty()) totalViews / observedVideoIds.size else null,
            cumulativeViewCurve = curve,
            unavailableReason = when {
                videos.isEmpty() -> COHORT_NO_VIDEOS
                // 조회수를 주는 플랫폼이 아예 없다 — 기다려도 채워지지 않는다.
                !viewsCollectable -> COHORT_VIEWS_NOT_COLLECTED
                !normalizable -> COHORT_NO_VIEWS
                else -> null
            },
        )
    }
}
