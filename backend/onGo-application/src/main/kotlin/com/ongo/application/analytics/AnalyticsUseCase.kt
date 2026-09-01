package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.enums.Platform
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AnalyticsUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val creditRepository: CreditRepository
) {

    @Cacheable(value = ["dashboardKpi"], key = "#userId + '-' + #days")
    fun getDashboardKpi(userId: Long, days: Int): DashboardKpiResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val kpi = analyticsRepository.getDashboardKpi(userId, days)
        val credit = creditRepository.findByUserId(userId)

        return DashboardKpiResponse(
            totalViews = kpi.totalViews,
            viewsChangePercent = kpi.totalViewsChange,
            totalSubscribers = kpi.totalSubscribers,
            subscribersChange = kpi.totalSubscribersChange,
            totalLikes = kpi.totalLikes,
            likesChangePercent = kpi.totalLikesChange,
            totalComments = kpi.totalComments,
            creditBalance = credit?.balance ?: 0,
            creditTotal = credit?.freeMonthly ?: user.planType.freeCredits
        )
    }

    @Cacheable(value = ["trendData"], key = "#userId + '-' + #days")
    fun getTrends(userId: Long, days: Int): TrendDataResponse {
        val trendData = analyticsRepository.getTrendData(userId, days)
        val grouped = trendData.groupBy { it.date }

        /*
         * **플랫폼이 실제로 수집하는 행만 더한다.**
         *
         * `TrendData.subscribers` 는 `SUM(ad.subscriber_gained)` 인데
         * ([AnalyticsJooqRepository] `getTrendData`), 그 컬럼을 채우는 어댑터는
         * `YouTubeClient` 하나뿐이다. 나머지는 0 을 하드코딩하므로 예전에는 12개 플랫폼의
         * 0 이 합계에 들어가고 `platformSubscribers` 에도 **플랫폼마다 "+0"** 이 실렸다.
         * 화면은 그것을 "신규 구독 0명" 이라는 성과로 그렸다.
         *
         * 조회수도 마찬가지다. Tumblr 의 `views` 는 노트 총합이고 Naver Clip 은 분석
         * API 자체가 없다 — 둘 다 조회수 표본이 아니다.
         */
        val points = grouped.mapNotNull { (date, items) ->
            val viewRows = items.filter {
                it.platform != null &&
                    PlatformMetricAvailability.isAvailable(it.platform!!.name, PlatformMetricAvailability.VIEWS)
            }
            val subscriberRows = items.filter {
                it.platform != null &&
                    PlatformMetricAvailability.isAvailable(
                        it.platform!!.name,
                        PlatformMetricAvailability.SUBSCRIBER_GAINED,
                    )
            }

            // 조회수를 수집하는 행이 하나도 없는 날짜는 점 자체를 만들지 않는다.
            if (viewRows.isEmpty()) return@mapNotNull null

            TrendPoint(
                date = date,
                totalViews = viewRows.sumOf { it.views },
                platformViews = viewRows.associate { it.platform!!.name to it.views },
                // 구독 증가를 주는 플랫폼이 없으면 합계 자체가 없다. 0 은 "늘지 않았다" 는 관측이 된다.
                totalSubscribers = if (subscriberRows.isEmpty()) null else subscriberRows.sumOf { it.subscribers },
                // 지도에 키가 없다는 것이 곧 "그 플랫폼은 이 지표를 주지 않는다" 이다.
                platformSubscribers = subscriberRows.associate { it.platform!!.name to it.subscribers },
                unavailableMetrics = if (subscriberRows.isEmpty()) {
                    setOf(PlatformMetricAvailability.SUBSCRIBER_GAINED)
                } else {
                    emptySet()
                },
            )
        }.sortedBy { it.date }

        return TrendDataResponse(data = points)
    }

    fun getVideoAnalytics(userId: Long, videoId: Long, days: Int): VideoAnalyticsResponse {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }
        val uploads = videoUploadRepository.findByVideoId(videoId)
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()

        // Batch fetch all analytics data for all uploads (eliminates N+1)
        val uploadIds = uploads.mapNotNull { it.id }
        val analyticsByUploadId = analyticsRepository.findByVideoUploadIdsAndDateRange(uploadIds, from, to)

        /*
         * **이 화면은 플랫폼별로 나눠 보여주므로 어떤 지표를 물어볼 수 있는지도 플랫폼마다
         * 정확히 알 수 있다.** 예전에는 그대로 raw 합계를 냈다.
         *
         * - Facebook·WordPress·Vimeo: 공유를 조회하지 않아 저장된 0 → "공유 0회"
         * - Pinterest: 댓글을 조회하지 않아 0, 그리고 `shares` 자리에는 **PIN_CLICK(클릭 수)**
         * - Dailymotion: `shares` 자리에 **bookmarks_total(북마크 수)**
         * - Tumblr: `views` 자리에 **total_notes(노트 총합)**
         *
         * 뒤의 셋은 0 이 아니라 **다른 뜻의 큰 숫자**라 더 조용히 틀린다.
         */
        val platforms = uploads.map { upload ->
            val dailyData = analyticsByUploadId[upload.id!!] ?: emptyList()
            fun reported(metric: String) =
                PlatformMetricAvailability.isAvailable(upload.platform.name, metric)

            val unavailable = listOf(
                PlatformMetricAvailability.VIEWS,
                PlatformMetricAvailability.LIKES,
                PlatformMetricAvailability.COMMENTS,
                PlatformMetricAvailability.SHARES,
            ).filterNot { reported(it) }.toSet()

            /*
             * **`sumOf` 를 그냥 부르지 않는다 — 빈 목록의 합은 `0` 이다.**
             *
             * 지원하는 지표라도 그 기간에 집계 행이 하나도 없으면 예전에는 `0` 이 나갔다.
             * 화면은 `dailyData.length` 로 그 상태를 숨길 수 있었지만, **JSON 계약 자체가
             * "0 회 측정됨" 이라고 말하고 있었다** — 공개 API 소비자에게는 그게 전부다.
             *
             * 두 `null` 은 [PlatformAnalyticsDetail.unavailableMetrics] 로 갈린다.
             * 지표가 그 집합에 있으면 플랫폼이 주지 않는 것(영원히 못 잼)이고, 없으면
             * 수집하지만 아직 행이 없는 것(수집 대기)이다. 행이 있고 합이 `0` 이면 실측이다.
             */
            fun sumIfMeasured(metric: String, pick: (com.ongo.domain.analytics.AnalyticsDaily) -> Long): Long? =
                if (reported(metric)) measuredSum(dailyData, pick) else null

            PlatformAnalyticsDetail(
                platform = upload.platform,
                views = sumIfMeasured(PlatformMetricAvailability.VIEWS) { it.views.toLong() },
                likes = sumIfMeasured(PlatformMetricAvailability.LIKES) { it.likes.toLong() },
                comments = sumIfMeasured(PlatformMetricAvailability.COMMENTS) { it.commentsCount.toLong() },
                shares = sumIfMeasured(PlatformMetricAvailability.SHARES) { it.shares.toLong() },
                unavailableMetrics = unavailable,
                /*
                 * 조회수를 수집하지 않는 플랫폼이면 **일별 계열도 만들지 않는다.**
                 * 화면은 이 계열을 조회수 추이로 그리므로, Tumblr 의 노트 총합을 넣으면
                 * 그것이 곧 조회수 그래프가 된다.
                 */
                dailyData = if (reported(PlatformMetricAvailability.VIEWS)) {
                    dailyData.map { DailyMetric(it.date, it.views, it.likes, it.commentsCount, it.shares) }
                } else {
                    emptyList()
                },
            )
        }

        return VideoAnalyticsResponse(videoId = videoId, title = video.title, platforms = platforms)
    }

    fun getHeatmap(userId: Long): HeatmapResponse {
        val data = analyticsRepository.getHeatmapData(userId)
        return HeatmapResponse(data = data)
    }

    fun getTopVideos(userId: Long, days: Int, limit: Int): TopVideoResponse {
        val topVideos = analyticsRepository.getTopVideos(userId, days, limit)

        // Batch fetch all uploads for top videos (eliminates N+1)
        val videoIds = topVideos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        // 조회 기간의 실제 집계를 붙인다. 예전에는 0 을 넣고 "populated from aggregate
        // query" 주석만 남겨 둬서, 화면에는 조회수 0 인 인기 영상이 나열됐다.
        // getVideoComparison 이 쓰는 것과 같은 배치 조회를 재사용한다(추가 쿼리 없음).
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()
        val allUploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }
        val analyticsByUploadId = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, from, to)

        val items = topVideos.map { video ->
            val videoId = video.id!!
            val uploads = uploadsByVideoId[videoId] ?: emptyList()

            /*
             * **지표를 수집하는 업로드의 행만 더한다.**
             *
             * 여러 플랫폼에 올린 영상이라 raw 합계는 서로 다른 뜻의 숫자를 섞는다.
             * Tumblr 의 `views` 는 노트 총합이라 그대로 더하면 인기 영상 순위가 뒤바뀐다.
             */
            fun uploadsReporting(metric: String) =
                uploads.filter { PlatformMetricAvailability.isAvailable(it.platform.name, metric) }

            fun rowsOf(selected: List<VideoUpload>) =
                selected.mapNotNull { it.id }.flatMap { analyticsByUploadId[it] ?: emptyList() }

            val viewUploads = uploadsReporting(PlatformMetricAvailability.VIEWS)
            val likeUploads = uploadsReporting(PlatformMetricAvailability.LIKES)
            val unavailable = buildSet {
                if (viewUploads.isEmpty()) add(PlatformMetricAvailability.VIEWS)
                if (likeUploads.isEmpty()) add(PlatformMetricAvailability.LIKES)
            }

            TopVideoItem(
                id = videoId,
                title = video.title,
                thumbnailUrl = video.thumbnailUrls.firstOrNull(),
                // 물어볼 곳이 없어도, 기간에 행이 없어도 합계는 없다. [measuredSum] 참고.
                totalViews = measuredSum(rowsOf(viewUploads)) { it.views.toLong() },
                totalLikes = measuredSum(rowsOf(likeUploads)) { it.likes.toLong() },
                unavailableMetrics = unavailable,
                publishedAt = video.createdAt,
                platforms = uploads.map { it.platform.name }
            )
        }
        return TopVideoResponse(videos = items)
    }

    fun getVideoComparison(userId: Long, videoIds: List<Long>, days: Int): VideoCompareResponse {
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()

        // Batch fetch all videos and uploads (eliminates N+1)
        val videos = videoRepository.findByIds(videoIds).associateBy { it.id!! }

        // Verify ownership for all requested videos
        videos.values.forEach { video ->
            if (video.userId != userId) {
                throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
            }
        }

        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        // Batch fetch all analytics
        val allUploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }
        val analyticsByUploadId = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, from, to)

        val items = videoIds.map { videoId ->
            val video = videos[videoId] ?: throw NotFoundException("영상", videoId)
            val uploads = uploadsByVideoId[videoId] ?: emptyList()
            val uploadIds = uploads.mapNotNull { it.id }

            val allAnalytics = uploadIds.flatMap { analyticsByUploadId[it] ?: emptyList() }

            /*
             * **합계마다 그 지표를 수집하는 업로드의 행만 더한다.**
             *
             * 예전에는 참여율만 걸렀고 `totalLikes`·`totalComments`·`totalShares`·
             * `totalWatchTime` 은 raw 합계였다. 같은 응답 안에서 참여율은 Facebook 공유를
             * 빼고 계산하는데 `totalShares` 에는 그 0 이 들어가는 모순이 있었다.
             *
             * 이름이 다른 지표는 0 이 아니라 큰 숫자라 더 나쁘다 — Pinterest 의 PIN_CLICK
             * (클릭 수)과 Dailymotion 의 bookmarks_total(북마크)이 `totalShares` 에,
             * Tumblr 의 total_notes(노트 총합)가 `totalViews` 에 그대로 더해졌다.
             */
            /*
             * **"그 지표를 물어볼 수 있는 업로드가 있는가" 와 "아직 수집된 행이 없다" 는
             * 서로 다른 상태다.**
             *
             * 앞은 플랫폼 계약의 문제라 `null` 이고, 뒤는 아직 동기화가 돌지 않았다는
             * 뜻이라 기존 계약대로 합계 0 을 유지한다. 둘을 뭉치면 동기화 직후의 영상이
             * "이 플랫폼은 이 지표를 주지 않는다" 로 잘못 표시된다.
             */
            fun uploadsReporting(metric: String) =
                uploads.filter { PlatformMetricAvailability.isAvailable(it.platform.name, metric) }

            fun rowsOf(selected: List<VideoUpload>) =
                selected.mapNotNull { it.id }.flatMap { analyticsByUploadId[it] ?: emptyList() }

            val viewUploads = uploadsReporting(PlatformMetricAvailability.VIEWS)
            val likeUploads = uploadsReporting(PlatformMetricAvailability.LIKES)
            val commentUploads = uploadsReporting(PlatformMetricAvailability.COMMENTS)
            val shareUploads = uploadsReporting(PlatformMetricAvailability.SHARES)
            val watchUploads = uploadsReporting(PlatformMetricAvailability.WATCH_TIME_SECONDS)

            // 물어볼 곳이 없어도, 기간에 행이 없어도 합계는 없다. [measuredSum] 참고.
            val totalViews = measuredSum(rowsOf(viewUploads)) { it.views.toLong() }
            val totalLikes = measuredSum(rowsOf(likeUploads)) { it.likes.toLong() }
            val totalComments = measuredSum(rowsOf(commentUploads)) { it.commentsCount.toLong() }
            val totalShares = measuredSum(rowsOf(shareUploads)) { it.shares.toLong() }
            val totalWatchTime = measuredSum(rowsOf(watchUploads)) { it.watchTimeSeconds }

            val unavailableMetrics = buildSet {
                if (viewUploads.isEmpty()) add(PlatformMetricAvailability.VIEWS)
                if (likeUploads.isEmpty()) add(PlatformMetricAvailability.LIKES)
                if (commentUploads.isEmpty()) add(PlatformMetricAvailability.COMMENTS)
                if (shareUploads.isEmpty()) add(PlatformMetricAvailability.SHARES)
                if (watchUploads.isEmpty()) add(PlatformMetricAvailability.WATCH_TIME_SECONDS)
            }.toMutableSet()

            /*
             * 참여율은 **수집하는 구성 지표만 더하고, 하나라도 빠지면 불완전하다고 표시한다.**
             *
             * `getCrossPlatformComparison` 과 같은 계약이며 화면(`VideoCompareView`)도
             * `unavailable-a/b` 로 그 표시를 이미 그린다. 순위를 매기는
             * `PerformanceScoreUseCase` 와 정책이 다른 이유는, 저쪽은 채널 기준선과 견줘
             * **등수를 매기므로** 구성 지표가 다른 값끼리 비교하면 의미가 깨지기 때문이다.
             * 여기는 값 옆에 "불완전" 을 함께 보여주는 화면이다.
             */
            val measuredEngagement = uploads.sumOf { upload ->
                fun reported(metric: String) =
                    PlatformMetricAvailability.isAvailable(upload.platform.name, metric)
                (analyticsByUploadId[upload.id] ?: emptyList()).sumOf { daily ->
                    (if (reported(PlatformMetricAvailability.LIKES)) daily.likes.toLong() else 0L) +
                        (if (reported(PlatformMetricAvailability.COMMENTS)) daily.commentsCount.toLong() else 0L) +
                        (if (reported(PlatformMetricAvailability.SHARES)) daily.shares.toLong() else 0L)
                }
            }
            if (listOf(
                    PlatformMetricAvailability.LIKES,
                    PlatformMetricAvailability.COMMENTS,
                    PlatformMetricAvailability.SHARES,
                ).any { metric -> uploads.any { !PlatformMetricAvailability.isAvailable(it.platform.name, metric) } }
            ) {
                unavailableMetrics += "engagementRate"
            }
            val engagementRate = if (totalViews != null && totalViews > 0) {
                Math.round((measuredEngagement.toDouble() / totalViews) * 100 * 100) / 100.0
            } else {
                // 분모가 없으면 비율이 성립하지 않는다. 0.0 은 "참여가 없었다" 는 관측이 된다.
                unavailableMetrics += "engagementRate"
                null
            }

            val dayCount = allAnalytics.map { it.date }.distinct().size.coerceAtLeast(1)

            VideoCompareItem(
                videoId = videoId,
                title = video.title,
                totalViews = totalViews,
                totalLikes = totalLikes,
                totalComments = totalComments,
                totalShares = totalShares,
                totalWatchTimeSeconds = totalWatchTime,
                avgDailyViews = totalViews?.let { it / dayCount },
                engagementRate = engagementRate,
                unavailableMetrics = unavailableMetrics,
            )
        }

        return VideoCompareResponse(videos = items)
    }

    @Cacheable(value = ["optimalTimes"], key = "#userId + '-' + #platform")
    /**
     * 최적 게시 시간 추천.
     *
     * ## 예전에는 시각 자체가 가짜였다
     *
     * ```
     * val hour = record.createdAt?.hour ?: 12
     * ```
     *
     * `record` 는 `analytics_daily` 행이고 그 `createdAt` 은 **우리 DB 에 행이 만들어진
     * 시각**, 즉 `AnalyticsSyncScheduler` 가 돈 시각이다. 스케줄러는 고정 주기로 돌므로
     * 이 값은 동기화 시각에 몰린다 — 실제 게시 시각과 아무 관계가 없다.
     * `null` 이면 **정오로 가정**해 그 슬롯에 쌓기까지 했다.
     *
     * 그렇게 만든 요일 × 시각 슬롯을 화면은 "예상 조회수 · 참여율 · 신뢰도" 로 보여줬다.
     *
     * ## 지금은 실제 게시 시각만 쓴다
     *
     * `video_uploads.published_at` 이 있는 업로드만 표본에 넣는다. 없으면 **슬롯을 만들지
     * 않는다** — 시각을 모르는 게시물은 시각을 추천할 근거가 못 된다.
     *
     * 참여율 분자는 그 플랫폼이 실제로 보고하는 지표만 더한다
     * ([PlatformMetricAvailability]). 미지원 지표의 저장 0 을 더하면 참여율이 실제보다
     * 낮게 나온다. 조회수가 0 인 행도 분모가 없어 제외한다.
     */
    fun getOptimalPublishTimes(userId: Long, platform: Platform?): OptimalTimesResponse {
        /*
         * 게시 시각과 플랫폼은 `findCrossPlatformDetailMetrics` 가 함께 준다
         * (V(현재) 이후 `publishedAt` 은 `video_uploads.published_at` 을 읽는다).
         * 새 쿼리를 만들지 않는다.
         */
        val uploads = analyticsRepository.findCrossPlatformDetailMetrics(userId, OPTIMAL_TIME_WINDOW_DAYS)
            .filter { platform == null || it.platform == platform.name }
            // 게시 시각을 모르면 시각을 추천할 근거가 없다. 정오로 가정하지 않는다.
            .filter { it.publishedAt != null }
            // 조회수가 0 이면 참여율의 분모가 없고 성과를 비교할 것도 없다.
            .filter { it.views > 0 }
            /*
             * 조회수를 **수집하지 않는 플랫폼**의 행은 표본이 아니다.
             *
             * Naver Clip 이 그렇다 — `NaverClipClient.getVideoAnalytics` 는 값을 돌려주지
             * 않고 예외를 던지므로 그 행의 숫자는 전부 컬럼 기본값이다. 알 수 없는 플랫폼도
             * 같이 막는다(fail-closed).
             */
            .filter { PlatformMetricAvailability.isAvailable(it.platform, PlatformMetricAvailability.VIEWS) }

        if (uploads.isEmpty()) {
            return OptimalTimesResponse(
                slots = emptyList(),
                unavailableReason = OPTIMAL_TIMES_UNAVAILABLE,
            )
        }

        val dayLabels = arrayOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")

        data class SlotKey(val dayOfWeek: Int, val hour: Int)
        data class SlotData(
            val views: MutableList<Long> = mutableListOf(),
            val engagements: MutableList<Double> = mutableListOf(),
            var sampleCount: Int = 0,
        )

        val slotMap = mutableMapOf<SlotKey, SlotData>()
        for (upload in uploads) {
            val publishedAt = upload.publishedAt!!
            val key = SlotKey(publishedAt.dayOfWeek.value % 7, publishedAt.hour)
            val data = slotMap.getOrPut(key) { SlotData() }

            data.views.add(upload.views)

            /*
             * 참여율 분자는 **그 플랫폼이 보고하는 지표만** 더한다.
             *
             * Facebook·WordPress·Vimeo 는 공유를, Pinterest 는 댓글을 주지 않아 저장된 0 은
             * 자리 채우기다. 그대로 더하면 그 플랫폼 슬롯의 참여율이 실제보다 낮게 나와
             * 추천 순위가 뒤바뀐다. 지원 지표의 측정된 0 은 그대로 더한다.
             */
            val unavailable = PlatformMetricAvailability.forPlatform(upload.platform)
            val reported = listOf(
                PlatformMetricAvailability.LIKES to upload.likes,
                PlatformMetricAvailability.COMMENTS to upload.comments,
                PlatformMetricAvailability.SHARES to upload.shares,
            ).filter { (metric, _) -> metric !in unavailable }

            /*
             * 참여 지표를 **하나도** 수집하지 않는 플랫폼이면 참여율 표본을 만들지 않는다.
             * 0 을 넣으면 그 슬롯의 중앙값이 내려가 "참여가 없는 시간대" 라는 관측이 된다 —
             * 재지 않았을 뿐인데 추천 순위가 뒤바뀐다.
             *
             * 조회수 표본은 그대로 남긴다. 조회수는 이 플랫폼이 수집하는 것이 확인된
             * 값이다(위 필터).
             */
            if (reported.isNotEmpty()) {
                val engagements = reported.sumOf { (_, value) -> value }
                data.engagements.add(engagements.toDouble() / upload.views * 100)
            }
            data.sampleCount++
        }

        /*
         * **참여율은 표본이 없으면 `null` 이다.**
         *
         * 위 루프는 참여 지표를 하나도 보고하지 않는 플랫폼의 행을 참여율 표본에서 뺀다.
         * 그런데 예전에는 남은 목록이 비어 있어도 `medianDouble(emptyList()) = 0.0` 이
         * 나와, 걸러낸 그 값이 **슬롯 단위에서 0 으로 되살아났다.** 화면은 "참여율 0%",
         * AI 프롬프트는 "참여율 0.00%" 로 받았다.
         *
         * 표본이 있으면 그 중앙값은 실측이다 — 0.0 이어도 그대로 둔다.
         */
        val engagementByKey = slotMap.mapValues { (_, data) ->
            if (data.engagements.isEmpty()) null else medianDouble(data.engagements)
        }

        /*
         * **참여 항을 점수에 넣을지는 슬롯 전체를 보고 정한다.**
         *
         * 점수는 오직 슬롯끼리의 **순위**에만 쓰인다(`sortedByDescending`). 그런데 세
         * 구성요소는 단위가 서로 다르다 — 조회수는 무한대로 열린 개수, 참여율은 0~100,
         * 신뢰도는 0~100 이다. 그래서 빠진 항을 다루는 방법마다 순위가 달라진다.
         *
         * - 예전처럼 `0` 을 넣으면: 재지 못한 슬롯이 참여 항 전체(가중치 0.3)를 잃어
         *   **측정되지 않았다는 이유로 벌점**을 받는다.
         * - 남은 가중치로 재정규화하면: 조회수 항이 0.6 → 0.857 로 커져, 같은 조회수라도
         *   **재지 않은 슬롯이 더 높은 점수**를 받는다. 측정하지 않은 쪽에 상을 준다.
         *
         * 둘 다 틀렸다. 그래서 **한 슬롯이라도 참여율을 재지 못했으면 참여 항을 모든
         * 슬롯에서 뺀다.** 모든 슬롯이 같은 구성요소로 비교되므로 측정 격차가 순위를
         * 바꾸지 않는다. 참여율을 전부 잰 경우에만 예전 그대로 3항 점수를 쓴다.
         */
        val scoreIncludesEngagement = engagementByKey.values.all { it != null }

        val slots = slotMap.map { (key, data) ->
            val medianViews = median(data.views)
            val medianEngagement = engagementByKey.getValue(key)
            /*
             * 신뢰도는 **실제 표본 수**로만 정한다. 시각을 모르는 행은 애초에 여기까지
             * 오지 않으므로, 이 수는 전부 게시 시각이 확인된 게시물이다.
             */
            val confidence = (data.sampleCount.coerceAtMost(MAX_CONFIDENCE_SAMPLES).toDouble() /
                MAX_CONFIDENCE_SAMPLES) * 100.0
            val engagementTerm = if (scoreIncludesEngagement) medianEngagement!! * 100 * 0.3 else 0.0
            val score = medianViews * 0.6 + engagementTerm + confidence * 0.1

            OptimalTimeSlot(
                dayOfWeek = key.dayOfWeek,
                dayLabel = dayLabels[key.dayOfWeek],
                hour = key.hour,
                timeLabel = "${key.hour.toString().padStart(2, '0')}:00",
                expectedViews = medianViews,
                engagementRate = medianEngagement?.let { Math.round(it * 100) / 100.0 },
                confidenceScore = Math.round(confidence * 100) / 100.0,
                score = Math.round(score * 100) / 100.0,
            )
        }
            .sortedByDescending { it.score }
            .take(5)

        return OptimalTimesResponse(slots = slots, unavailableReason = null)
    }

    /**
     * **행이 있을 때만 합계를 낸다.**
     *
     * `emptyList().sumOf { .. }` 는 `0` 이다. 그 `0` 이 그대로 응답에 들어가면, 지원
     * 플랫폼에 올렸지만 **그 기간에 아직 동기화되지 않은** 영상이 "조회수 0회" 로 나간다 —
     * 실제로 0 회였던 영상과 완전히 같은 모양이다.
     *
     * 세 상태는 이렇게 갈린다.
     *
     * - `null` + 지표가 `unavailableMetrics` 에 있음 → 그 지표를 주는 플랫폼이 없다.
     * - `null` + 지표가 `unavailableMetrics` 에 **없음** → 수집하지만 기간 내 행이 없다.
     * - 숫자 → 실측. 행이 있고 합이 `0` 이면 그 `0` 은 관측이다.
     *
     * `unavailableMetrics` 의 의미(플랫폼 계약)는 바뀌지 않으므로 새 필드가 필요 없다.
     */
    private inline fun <T> measuredSum(rows: List<T>, pick: (T) -> Long): Long? =
        if (rows.isEmpty()) null else rows.sumOf(pick)

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun medianDouble(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    fun getTagPerformance(userId: Long, days: Int): TagPerformanceResponse {
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()
        val previousFrom = from.minusDays(days.toLong())

        // Get all user's videos
        val videos = videoRepository.findByUserId(userId, 0, 1000)
        if (videos.isEmpty()) return TagPerformanceResponse(tags = emptyList())

        // Group videos by tags
        val tagVideoMap = mutableMapOf<String, MutableList<Video>>()
        for (video in videos) {
            for (tag in video.tags) {
                tagVideoMap.getOrPut(tag) { mutableListOf() }.add(video)
            }
        }

        // Batch fetch all uploads
        val allVideoIds = videos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(allVideoIds)
        val allUploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }

        // Batch fetch analytics for current and previous periods
        val currentAnalytics = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, from, to)
        val previousAnalytics = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, previousFrom, from.minusDays(1))

        val tagItems = tagVideoMap.map { (tag, tagVideos) ->
            val tagVideoIds = tagVideos.mapNotNull { it.id }
            val tagUploads = tagVideoIds.flatMap { uploadsByVideoId[it] ?: emptyList() }

            /*
             * **태그는 여러 영상·여러 플랫폼에 걸쳐 있어 raw 합계가 서로 다른 뜻의 숫자를
             * 섞는다.** Tumblr 의 `views` 는 노트 총합이고 Pinterest 의 `likes` 는
             * 저장(Save) 수라, 0 이 아니라 **큰 숫자로 조용히 틀린다.**
             *
             * "물어볼 수 있는 업로드가 있는가" 와 "아직 집계 행이 없다" 는 다른 상태다.
             * 앞은 `null`, 뒤는 기존 계약대로 합계 0 이다.
             */
            fun uploadsReporting(vararg metrics: String) = tagUploads.filter { upload ->
                metrics.all { PlatformMetricAvailability.isAvailable(upload.platform.name, it) }
            }

            fun rowsOf(selected: List<VideoUpload>, source: Map<Long, List<AnalyticsDaily>>) =
                selected.mapNotNull { it.id }.flatMap { source[it] ?: emptyList() }

            val viewUploads = uploadsReporting(PlatformMetricAvailability.VIEWS)
            val likeUploads = uploadsReporting(PlatformMetricAvailability.LIKES)
            // 참여율은 분자와 분모가 **같은 행**에서 나와야 한다.
            val engagementUploads =
                uploadsReporting(PlatformMetricAvailability.LIKES, PlatformMetricAvailability.VIEWS)

            // 물어볼 곳이 없어도, 기간에 행이 없어도 합계는 없다. [measuredSum] 참고.
            val totalViews = measuredSum(rowsOf(viewUploads, currentAnalytics)) { it.views.toLong() }
            val totalLikes = measuredSum(rowsOf(likeUploads, currentAnalytics)) { it.likes.toLong() }

            val videoCount = tagVideos.size
            val avgViews = totalViews?.let { it / videoCount }

            val engagementRows = rowsOf(engagementUploads, currentAnalytics)
            val engagementViews = engagementRows.sumOf { it.views.toLong() }
            val avgEngagement = if (engagementUploads.isNotEmpty() && engagementViews > 0) {
                val likes = engagementRows.sumOf { it.likes.toLong() }
                Math.round((likes.toDouble() / engagementViews) * 100 * 100) / 100.0
            } else {
                // 분모가 없으면 비율이 성립하지 않는다. 0.0 은 "참여가 없었다" 는 관측이 된다.
                null
            }

            /*
             * 추세는 **관측된 이전 기간**이 있어야 말할 수 있다.
             *
             * 예전에는 `prevViews == 0L -> "stable"` 이라, 이전 기간에 행이 하나도 없어도
             * "변화 없음" 이라는 관측이 됐다. 행이 있는 상태의 `0 → N` 은 실제 증가이므로
             * 그 판정은 그대로 둔다.
             */
            val prevViewRows = rowsOf(viewUploads, previousAnalytics)
            val trend = if (viewUploads.isEmpty() || prevViewRows.isEmpty() || totalViews == null) {
                null
            } else {
                val prevViews = prevViewRows.sumOf { it.views.toLong() }
                when {
                    prevViews == 0L && totalViews > 0 -> "up"
                    prevViews == 0L -> "stable"
                    totalViews > prevViews * 1.1 -> "up"
                    totalViews < prevViews * 0.9 -> "down"
                    else -> "stable"
                }
            }

            TagPerformanceItem(
                tag = tag,
                videoCount = videoCount,
                totalViews = totalViews,
                totalLikes = totalLikes,
                avgViews = avgViews,
                avgEngagement = avgEngagement,
                trend = trend,
                unavailableMetrics = buildSet {
                    if (viewUploads.isEmpty()) add(PlatformMetricAvailability.VIEWS)
                    if (likeUploads.isEmpty()) add(PlatformMetricAvailability.LIKES)
                },
            )
        }.sortedByDescending { it.totalViews ?: -1L }

        return TagPerformanceResponse(tags = tagItems)
    }

    fun getCrossPlatformComparison(userId: Long, days: Int): CrossPlatformSummaryResponse {
        val rawData = analyticsRepository.findCrossPlatformMetrics(userId, days)
        if (rawData.isEmpty()) return CrossPlatformSummaryResponse(videos = emptyList(), platformRankings = emptyMap())

        // 영상별 그룹핑
        val byVideo = rawData.groupBy { it.videoId }
        val videos = byVideo.map { (videoId, items) ->
            val platforms = items.map { raw ->
                val unavailableMetrics = PlatformMetricAvailability.forPlatform(raw.platform)
                fun <T> measured(metric: String, value: T): T? =
                    if (metric in unavailableMetrics) null else value

                val measuredEngagement = listOf(
                    PlatformMetricAvailability.LIKES to raw.likes,
                    PlatformMetricAvailability.COMMENTS to raw.comments,
                    PlatformMetricAvailability.SHARES to raw.shares,
                ).filterNot { (metric, _) -> metric in unavailableMetrics }
                    .sumOf { (_, value) -> value }

                /*
                 * 조회수 자체가 미수집이면 분모가 없다. Tumblr 의 `views` 는 노트 총합이라
                 * 그것으로 나누면 참여율이 100% 근처로 나온다.
                 */
                val viewsMeasured = measured(PlatformMetricAvailability.VIEWS, raw.views)
                val engagementRate = if (viewsMeasured != null && viewsMeasured > 0) {
                    Math.round((measuredEngagement.toDouble() / viewsMeasured) * 100 * 100) / 100.0
                } else {
                    null
                }

                PlatformMetrics(
                    platform = raw.platform,
                    // **숫자도 함께 비운다.** 예전에는 `unavailableMetrics` 로 알리면서도
                    // raw 값을 그대로 내보내, 그것을 읽는 소비자가 저장 수를 좋아요로 받았다.
                    views = viewsMeasured,
                    likes = measured(PlatformMetricAvailability.LIKES, raw.likes),
                    comments = measured(PlatformMetricAvailability.COMMENTS, raw.comments),
                    shares = measured(PlatformMetricAvailability.SHARES, raw.shares),
                    watchTimeSeconds =
                        measured(PlatformMetricAvailability.WATCH_TIME_SECONDS, raw.watchTimeSeconds),
                    engagementRate = engagementRate,
                    avgViewDuration =
                        measured(PlatformMetricAvailability.AVG_VIEW_DURATION, raw.avgViewDurationSeconds),
                    revenueMicro = measured(PlatformMetricAvailability.REVENUE_MICRO, raw.revenueMicro),
                    unavailableMetrics = unavailableMetrics,
                )
            }

            // 측정된 참여율만 후보다. `null` 을 0 으로 보면 미수집 플랫폼이 최고가 될 수 있다.
            val bestPlatform = platforms
                .mapNotNull { p -> p.engagementRate?.let { p.platform to it } }
                .maxByOrNull { it.second }
                ?.first
            val insights = generateInsights(platforms)

            CrossPlatformComparisonResponse(
                videoId = videoId,
                videoTitle = items.first().videoTitle,
                platforms = platforms,
                bestPlatform = bestPlatform,
                insights = insights,
            )
        }

        // 플랫폼별 순위
        val byPlatform = rawData.groupBy { it.platform }
        val platformRankings = byPlatform.map { (platform, items) ->
            /*
             * **순위는 raw 합계로 매기면 안 된다.**
             *
             * 예전에는 `sumOf { it.views }` 로 정렬해 Tumblr 의 노트 총합이 조회수로
             * 들어갔고, `sumOf { likes + comments + shares }` 에는 Pinterest 의 저장 수와
             * 클릭 수가 함께 섞였다. 그 숫자로 "가장 성과가 좋은 플랫폼" 을 뽑았다.
             */
            val unavailable = PlatformMetricAvailability.forPlatform(platform)
            fun reports(metric: String) = metric !in unavailable

            val totalViews = if (reports(PlatformMetricAvailability.VIEWS)) items.sumOf { it.views } else null
            val totalRevenue =
                if (reports(PlatformMetricAvailability.REVENUE_MICRO)) items.sumOf { it.revenueMicro } else null

            val engagementMetrics = listOf(
                PlatformMetricAvailability.LIKES to { r: CrossPlatformRaw -> r.likes },
                PlatformMetricAvailability.COMMENTS to { r: CrossPlatformRaw -> r.comments },
                PlatformMetricAvailability.SHARES to { r: CrossPlatformRaw -> r.shares },
            ).filter { (metric, _) -> reports(metric) }
            val totalEngagements = items.sumOf { row -> engagementMetrics.sumOf { (_, pick) -> pick(row) } }

            val avgEngagementRate = if (
                totalViews != null && totalViews > 0 && engagementMetrics.isNotEmpty()
            ) {
                Math.round((totalEngagements.toDouble() / totalViews) * 100 * 100) / 100.0
            } else {
                null
            }

            platform to PlatformRanking(
                platform = platform,
                avgEngagementRate = avgEngagementRate,
                totalViews = totalViews,
                totalRevenue = totalRevenue,
                rank = null, // 아래에서 측정된 플랫폼에만 설정
                unavailableMetrics = listOf(
                    PlatformMetricAvailability.VIEWS,
                    PlatformMetricAvailability.LIKES,
                    PlatformMetricAvailability.COMMENTS,
                    PlatformMetricAvailability.SHARES,
                    PlatformMetricAvailability.REVENUE_MICRO,
                ).filter { it in unavailable }.toSet(),
            )
        }
            .let { entries ->
                /*
                 * 조회수가 측정된 플랫폼만 순위를 매긴다. 측정되지 않은 플랫폼은 목록에는
                 * 남되 `rank = null` 이다 — 0 을 주면 최상위로 읽힌다.
                 */
                val ranked = entries
                    .filter { it.second.totalViews != null }
                    .sortedByDescending { it.second.totalViews }
                    .mapIndexed { index, (platform, ranking) -> platform to ranking.copy(rank = index + 1) }
                val unranked = entries.filter { it.second.totalViews == null }
                ranked + unranked
            }
            .toMap()

        return CrossPlatformSummaryResponse(videos = videos, platformRankings = platformRankings)
    }

    private fun generateInsights(platforms: List<PlatformMetrics>): List<String> {
        val insights = mutableListOf<String>()
        if (platforms.size < 2) return insights

        /*
         * **측정된 값만 후보로 삼는다.** `null` 을 0 으로 보면 미수집 플랫폼이 "가장 높은
         * 조회수" 로 뽑히거나, 노트 총합이 조회수 1위가 된다.
         */
        val bestViews = platforms.mapNotNull { p -> p.views?.let { p to it } }.maxByOrNull { it.second }
        val bestEngagement =
            platforms.mapNotNull { p -> p.engagementRate?.let { p to it } }.maxByOrNull { it.second }
        val bestRevenue = platforms.mapNotNull { p -> p.revenueMicro?.let { p to it } }.maxByOrNull { it.second }

        if (bestViews != null) {
            insights.add("${bestViews.first.platform}에서 가장 높은 조회수(${bestViews.second.formatCompact()})를 기록했습니다")
        }
        if (bestEngagement != null && bestEngagement.first.platform != bestViews?.first?.platform) {
            insights.add("${bestEngagement.first.platform}의 참여율(${bestEngagement.second}%)이 가장 높습니다")
        }
        if (bestRevenue != null && bestRevenue.second > 0) {
            insights.add("${bestRevenue.first.platform}에서 가장 높은 수익을 창출했습니다")
        }

        return insights
    }

    private fun Long.formatCompact(): String = when {
        this >= 1_000_000 -> "${(this / 1_000_000.0).let { Math.round(it * 10) / 10.0 }}M"
        this >= 1_000 -> "${(this / 1_000.0).let { Math.round(it * 10) / 10.0 }}K"
        else -> this.toString()
    }

    fun getPlatformComparison(userId: Long, days: Int): PlatformComparisonResponse {
        // getTrendData 는 조회수만 집계해서, 예전에는 likes/comments/shares 를 0 으로
        // 채워 내보냈다. 플랫폼별 참여도를 비교하는 화면인데 참여 지표가 전부 0 이라
        // 비교 자체가 성립하지 않았다.
        //
        // findCrossPlatformDetailMetrics 는 같은 analytics_daily ⋈ video_uploads 를
        // 같은 기간으로 훑으면서 네 지표를 모두 집계한다. 새 쿼리를 만들지 않는다.
        val rows = analyticsRepository.findCrossPlatformDetailMetrics(userId, days)
        if (rows.isEmpty()) return PlatformComparisonResponse(platforms = emptyList())

        val summaries = rows
            .groupBy { it.platform }
            .mapNotNull { (platformName, data) ->
                // 저장된 문자열이 현재 enum 에 없으면 건너뛴다. 알 수 없는 플랫폼을
                // 0 으로 채워 넣으면 다시 같은 종류의 거짓 데이터가 된다.
                val platform = runCatching { Platform.valueOf(platformName) }.getOrNull()
                    ?: return@mapNotNull null
                /*
                 * **그 플랫폼이 주지 않는 지표를 숫자로 내리지 않는다.**
                 *
                 * Facebook·WordPress·Vimeo 는 공유를, Pinterest 는 댓글을 API 로 주지
                 * 않아 저장된 0 은 자리 채우기다. 그대로 합산하면 플랫폼 비교 화면이
                 * "Facebook 공유 0회" 를 성과처럼 보여준다.
                 *
                 * 지원하는 지표의 실제 0 은 관측 결과이므로 그대로 둔다.
                 * 같은 클래스의 [getVideoComparison] 이 이미 쓰는 계약이다.
                 */
                val unavailable = PlatformMetricAvailability.forPlatform(platformName)
                PlatformSummary(
                    platform = platform,
                    // 조회수도 예외가 아니다 — Tumblr 의 `views` 는 노트 총합이다.
                    views = data.sumOf { it.views }.takeIf { PlatformMetricAvailability.VIEWS !in unavailable },
                    likes = data.sumOf { it.likes }.takeIf { PlatformMetricAvailability.LIKES !in unavailable },
                    comments = data.sumOf { it.comments }.takeIf { PlatformMetricAvailability.COMMENTS !in unavailable },
                    shares = data.sumOf { it.shares }.takeIf { PlatformMetricAvailability.SHARES !in unavailable },
                    unavailableMetrics = listOf(
                        PlatformMetricAvailability.VIEWS,
                        PlatformMetricAvailability.LIKES,
                        PlatformMetricAvailability.COMMENTS,
                        PlatformMetricAvailability.SHARES,
                    ).filter { it in unavailable },
                )
            }
        return PlatformComparisonResponse(platforms = summaries)
    }

    /**
     * 트래픽 소스 분포.
     *
     * ## 지금은 **수집 경로가 없다**
     *
     * 이 값의 유일한 출처인 `channel_insights_daily` 는
     * [AnalyticsRepository.upsertChannelInsights] 로만 채워지는데, 그 함수는 저장소
     * 구현만 있고 **호출부가 하나도 없다**(`AnalyticsSyncScheduler` 도 부르지 않는다).
     * 어댑터가 돌려주는 `PlatformAnalytics` 에도 트래픽 소스 필드가 없어, 지금 코드로는
     * 그 테이블이 채워질 방법 자체가 없다.
     *
     * 그래서 예전 응답의 `sources = {}` / `total = 0` 은 **"유입 0 건" 이라는 관측처럼
     * 보였다.** 실제로는 재지 않은 것이다. 임의의 분포를 지어내지 않고
     * [TrafficSourceResponse.available] 로 그 사실을 그대로 알린다.
     *
     * 판정은 행의 존재로 한다 — 나중에 수집이 붙으면 이 코드를 고치지 않아도 열린다.
     */
    fun getTrafficSources(userId: Long, days: Int): TrafficSourceResponse {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())
        val insights = analyticsRepository.findChannelInsights(userId, null, startDate, endDate)

        val merged = mutableMapOf<String, Long>()
        insights.forEach { day ->
            day.trafficSource.forEach { (source, count) ->
                merged[source] = (merged[source] ?: 0) + count
            }
        }

        /*
         * **행이 있다는 것과 관측이 있다는 것은 다르다.**
         *
         * 예전에는 `insights.isNotEmpty()` 로 판정했다. 그런데 `channel_insights_daily`
         * 행이 있어도 `traffic_source` JSONB 가 `{}` 면 유입을 잰 적이 없다. 그런데도
         * `available = true` 로 열려, 빈 분포가 **"유입 0 건"** 이라는 관측으로 보였다.
         *
         * 판정 기준은 **항목의 존재**다. 값이 아니라 키를 본다 — `SEARCH -> 0` 은
         * "검색 유입이 0 건이었다" 는 **관측**이므로 항목이 있고, 따라서 열린다.
         */
        val sources = merged.toSortedMap()

        return TrafficSourceResponse(
            period = "${days}d",
            sources = sources,
            total = merged.values.sum(),
            available = sources.isNotEmpty(),
            unavailableReason = if (sources.isEmpty()) CHANNEL_INSIGHTS_UNAVAILABLE_REASON else null,
        )
    }

    /**
     * 시청자 인구통계. **수집 경로가 없다** — 근거는 [getTrafficSources] 와 같다.
     *
     * 빈 분포는 "그런 시청자가 없었다" 가 아니라 재지 않았다는 뜻이다.
     */
    fun getDemographics(userId: Long, days: Int): DemographicsResponse {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())
        val insights = analyticsRepository.findChannelInsights(userId, null, startDate, endDate)

        val ageAccum = mutableMapOf<String, Double>()
        val genderAccum = mutableMapOf<String, Double>()
        val countryAccum = mutableMapOf<String, Long>()

        insights.forEach { day ->
            day.demographicsAge.forEach { (k, v) -> ageAccum[k] = (ageAccum[k] ?: 0.0) + v }
            day.demographicsGender.forEach { (k, v) -> genderAccum[k] = (genderAccum[k] ?: 0.0) + v }
            day.demographicsCountry.forEach { (k, v) -> countryAccum[k] = (countryAccum[k] ?: 0) + v }
        }

        // 행이 없으면 위 분포는 전부 비어 있어 나눗셈이 일어나지 않는다.
        // `coerceAtLeast(1)` 은 0 으로 나누기를 막을 뿐 값을 만들어내지 않는다.
        val count = insights.size.coerceAtLeast(1)
        val ageDistribution = ageAccum.mapValues { Math.round(it.value / count * 10) / 10.0 }
        val genderDistribution = genderAccum.mapValues { Math.round(it.value / count * 10) / 10.0 }
        val topCountries = countryAccum.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }

        /*
         * **행이 있다는 것과 관측이 있다는 것은 다르다** — [getTrafficSources] 와 같은 이유다.
         *
         * 세 분포 중 **하나라도** 항목이 있으면 잰 것이다(부분 관측도 관측이다). 그때
         * 비어 있는 나머지 분포는 **빈 맵 그대로** 둔다 — 없는 항목을 지어내지 않는다.
         *
         * 판정은 값이 아니라 키로 한다. `"25-34" -> 0.0` 은 "그 연령대가 0% 였다" 는
         * 관측이므로 항목이 있고, 따라서 열린다.
         */
        val measured = ageDistribution.isNotEmpty() ||
            genderDistribution.isNotEmpty() ||
            topCountries.isNotEmpty()

        return DemographicsResponse(
            period = "${days}d",
            ageDistribution = ageDistribution,
            genderDistribution = genderDistribution,
            topCountries = topCountries,
            available = measured,
            unavailableReason = if (measured) null else CHANNEL_INSIGHTS_UNAVAILABLE_REASON,
        )
    }

    /**
     * CTR 추세.
     *
     * ## 예전에는 분자와 분모의 모집단이 달랐다
     *
     * `findAllByUserId` 로 **전 플랫폼** 행을 가져와 이렇게 계산했다.
     *
     * ```
     * val totalImpressions = records.sumOf { it.impressions }   // YouTube 만 실측
     * val totalViews = records.sumOf { it.views }               // 전 플랫폼 실측
     * val ctr = if (totalImpressions > 0) ... else 0.0
     * ```
     *
     * `impressions` 를 요청하는 어댑터는 [com.ongo.infrastructure.external.youtube.YouTubeClient]
     * 하나뿐이고 나머지 12 개는 그 값을 세팅하지 않아 0 으로 남는다. 결과는 둘 중 하나였다.
     *
     * - YouTube 가 없는 크리에이터: 분모가 0 이라 **CTR 이 항상 정확히 0.0%**. 화면은
     *   "평균 CTR 0% · 총 노출 0" 을 성과처럼 보여줬다 — 재지 않았을 뿐이다.
     * - YouTube + TikTok 혼합: 분자에 TikTok 조회수가 들어가고 분모는 YouTube 노출뿐이라
     *   **CTR 이 100% 를 넘었다.**
     *
     * ## 지금은 같은 행에서만 계산한다
     *
     * 노출이 실제로 측정된 행(`impressions > 0`)만 남기고, **그 행의 views 만** 분자에
     * 넣는다. 분자와 분모가 같은 관측에서 나온다.
     *
     * 측정 가능한 행이 하나도 없으면 `avgCTR`·`totalImpressions` 는 `null` 이고 `data` 는
     * 빈 배열이다. 0 을 만들지 않는다 — 0% 는 "클릭이 없었다" 는 관측 결과다.
     * 반대로 `impressions > 0` 인데 views 가 0 이면 그 0% 는 측정된 사실이므로 보존한다.
     */
    fun getCTRTrend(userId: Long, days: Int): CTRResponse {
        /*
         * 어떤 업로드가 노출을 보고하는 플랫폼의 것인지 확인한다.
         *
         * `findCrossPlatformDetailMetrics` 는 이미 `analytics_daily ⋈ video_uploads` 를 같은
         * 기간으로 훑으며 플랫폼을 함께 준다([getPlatformComparison] 이 쓰는 것과 같은 쿼리).
         * 새 쿼리를 만들지 않는다.
         *
         * 알 수 없는 플랫폼 문자열은 허용 목록에 넣지 않는다 — fail-closed.
         */
        val platformByUploadId = analyticsRepository.findCrossPlatformDetailMetrics(userId, days)
            .filter { PlatformMetricAvailability.isAvailable(it.platform, PlatformMetricAvailability.IMPRESSIONS) }
            .associate { it.videoUploadId to it.platform }
        val reportingUploadIds = platformByUploadId.keys

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())

        /*
         * 세 조건을 모두 만족하는 행만 남긴다.
         *
         * - 기간 안
         * - 노출을 보고하는 플랫폼의 업로드
         * - `impressions > 0` — 플랫폼이 보고하더라도 그날 값이 0 이면 분모가 없다
         *
         * 마지막 조건이 핵심이다. 플랫폼만 보고 통과시키면 YouTube 의 미수집 0 이 다시
         * 분모 자리에 들어간다.
         */
        val measured = analyticsRepository.findAllByUserId(userId).filter {
            it.date in startDate..endDate &&
                it.videoUploadId in reportingUploadIds &&
                it.impressions > 0
        }

        if (measured.isEmpty()) {
            return CTRResponse(
                period = "${days}d",
                avgCTR = null,
                totalImpressions = null,
                data = emptyList(),
                measuredPlatforms = emptyList(),
                unavailableReason = CTR_UNAVAILABLE,
            )
        }

        // 날짜별 포인트. 측정된 행이 없는 날짜는 **포인트를 만들지 않는다** —
        // 0 포인트를 그리면 그날 클릭률이 0 이었다는 관측이 된다.
        val dataPoints = measured.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, records) ->
            val impressions = records.sumOf { it.impressions.toLong() }
            val views = records.sumOf { it.views.toLong() }
            CTRTrendPoint(
                date = date.toString(),
                impressions = impressions,
                views = views,
                // 같은 행에서 나온 값끼리 나눈다. impressions > 0 이 보장돼 있다.
                ctr = Math.round((views.toDouble() / impressions * 100) * 100) / 100.0,
            )
        }

        val totalImpressions = dataPoints.sumOf { it.impressions }
        val totalViews = dataPoints.sumOf { it.views }

        return CTRResponse(
            period = "${days}d",
            avgCTR = Math.round((totalViews.toDouble() / totalImpressions * 100) * 100) / 100.0,
            totalImpressions = totalImpressions,
            data = dataPoints,
            /*
             * **실제로 합계에 들어간 행의 플랫폼**만 밝힌다.
             *
             * 예전에는 `platformsReporting(...)` — 노출을 보고할 수 있는 플랫폼 전체 —
             * 를 내보냈다. 그건 "이 크리에이터의 표본" 이 아니라 "이론상 가능한 목록" 이다.
             * YouTube 를 연동하지 않았는데도 목록에 YOUTUBE 가 실릴 수 있었다.
             */
            measuredPlatforms = measured.mapNotNull { platformByUploadId[it.videoUploadId] }
                .distinct()
                .sorted(),
            unavailableReason = null,
        )
    }

    /**
     * 평균 시청 시간 추세.
     *
     * ## 예전에는 분자와 분모의 모집단이 달랐다
     *
     * ```
     * val totalWatch = records.sumOf { it.watchTimeSeconds }   // YouTube 만 실측
     * val totalViews = records.sumOf { it.views }              // 전 플랫폼 실측
     * val avg = if (totalViews > 0) totalWatch / totalViews else 0L
     * ```
     *
     * `estimatedMinutesWatched` 를 요청하는 어댑터는
     * [com.ongo.infrastructure.external.youtube.YouTubeClient] 하나뿐이고
     * (`YouTubeClient.kt:149` metrics 목록, `:161` 파싱) 나머지 12 개는
     * `watchTimeSeconds = 0` 을 하드코딩한다. 그래서
     *
     * - YouTube 가 없는 크리에이터: 분자가 0 이라 **평균 시청 시간이 항상 0초**.
     *   `PerformanceView` 는 응답 객체가 항상 오므로 `—` 대신 "0초" 를 그렸다 —
     *   재지 않았을 뿐인데 시청이 없었다는 관측이 된다.
     * - YouTube + TikTok 혼합: 분모에 TikTok 조회수가 들어가 **평균이 구조적으로 짧게** 나왔다.
     *
     * ## 지금은 같은 관측에서만 계산한다
     *
     * 시청 시간을 보고하는 플랫폼의 행 중 `views > 0` 인 것만 남기고, **그 행의 값끼리만**
     * 나눈다. 유효한 행이 하나도 없으면 `avgDurationSeconds` 는 `null` 이고 `data` 는 빈 배열이다.
     *
     * 반대로 유효한 행이 있고 `watchTimeSeconds` 가 0 이면 그 0 은 **측정된 사실**이므로
     * 평균 0초를 그대로 보존한다.
     */
    fun getAvgViewDuration(userId: Long, days: Int): AvgViewDurationResponse {
        /*
         * 어떤 업로드가 시청 시간을 보고하는 플랫폼의 것인지 확인한다.
         * `findCrossPlatformDetailMetrics` 는 이미 플랫폼을 함께 주므로 새 쿼리가 없다.
         * 알 수 없는 플랫폼 문자열은 허용 목록에 넣지 않는다 — fail-closed.
         */
        val platformByUploadId = analyticsRepository.findCrossPlatformDetailMetrics(userId, days)
            .filter {
                PlatformMetricAvailability.isAvailable(it.platform, PlatformMetricAvailability.WATCH_TIME_SECONDS)
            }
            .associate { it.videoUploadId to it.platform }
        val reportingUploadIds = platformByUploadId.keys

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())

        /*
         * 기간 · 허용 uploadId · `views > 0` 세 조건을 모두 만족하는 행만 남긴다.
         *
         * `views > 0` 이 분모 조건이다. 조회가 없으면 평균 시청 시간을 만들 수 없다.
         * 이 필터를 통과한 행의 `watchTimeSeconds` 0 은 **측정된 0** 이다 —
         * 하드코딩 0 을 넣는 플랫폼은 애초에 허용 목록에 없다.
         */
        val measured = analyticsRepository.findAllByUserId(userId).filter {
            it.date in startDate..endDate &&
                it.videoUploadId in reportingUploadIds &&
                it.views > 0
        }

        if (measured.isEmpty()) {
            return AvgViewDurationResponse(
                period = "${days}d",
                avgDurationSeconds = null,
                data = emptyList(),
                measuredPlatforms = emptyList(),
                unavailableReason = WATCH_TIME_UNAVAILABLE,
            )
        }

        // 날짜별 포인트. 유효한 행이 없는 날짜는 **포인트를 만들지 않는다** —
        // 0 포인트를 그리면 그날 시청 시간이 0 이었다는 관측이 된다.
        val dataPoints = measured.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, records) ->
            val totalWatch = records.sumOf { it.watchTimeSeconds }
            val totalViews = records.sumOf { it.views.toLong() }
            AvgViewDurationPoint(
                date = date.toString(),
                // 같은 행에서 나온 값끼리 나눈다. views > 0 이 보장돼 있다.
                avgDurationSeconds = totalWatch / totalViews,
                totalWatchTimeSeconds = totalWatch,
                totalViews = totalViews,
            )
        }

        // 전체 평균은 조회수로 가중된다 — 합계끼리 나누므로 날짜별 평균의 산술평균이 아니다.
        val totalWatch = measured.sumOf { it.watchTimeSeconds }
        val totalViews = measured.sumOf { it.views.toLong() }

        return AvgViewDurationResponse(
            period = "${days}d",
            avgDurationSeconds = totalWatch / totalViews,
            data = dataPoints,
            // 실제로 합계에 들어간 행의 플랫폼만 밝힌다. 이론상 가능한 목록이 아니다.
            measuredPlatforms = measured.mapNotNull { platformByUploadId[it.videoUploadId] }
                .distinct()
                .sorted(),
            unavailableReason = null,
        )
    }

    /**
     * 구독 전환 추세.
     *
     * ## 예전에는 분자와 분모의 모집단이 달랐다
     *
     * ```
     * val gained = records.sumOf { it.subscriberGained }   // YouTube 만 실측
     * val views = records.sumOf { it.views }               // 전 플랫폼 실측
     * val rate = if (views > 0) ... else 0.0
     * ```
     *
     * `subscribersGained` 를 요청하는 어댑터는 [com.ongo.infrastructure.external.youtube.YouTubeClient]
     * 하나뿐이고 나머지 12 개는 `subscriberGained = 0` 을 하드코딩한다. 그래서
     *
     * - YouTube 가 없는 크리에이터: 분자가 0 이라 **전환율이 항상 0%**, `totalGained` 도 0.
     *   화면은 `총 신규 구독: **+0**` 을 초록색(`text-success-strong`)으로 보여줬다 —
     *   재지 않았을 뿐인데 성과 색이다.
     * - YouTube + TikTok 혼합: 분모에 TikTok 조회수가 들어가 **전환율이 구조적으로 낮게** 나왔다.
     *
     * ## 지금은 같은 관측에서만 계산한다
     *
     * 구독 증가를 보고하는 플랫폼의 행 중 `views > 0` 인 것만 남기고, **그 행의 값끼리만**
     * 나눈다. 유효한 행이 하나도 없으면 `totalGained` 는 `null` 이고 `data` 는 빈 배열이다.
     *
     * 반대로 유효한 행이 있고 `subscriberGained` 가 0 이면 그 0 은 **측정된 사실**이므로
     * 전환율 0% 와 `totalGained = 0` 을 그대로 보존한다.
     */
    fun getSubscriberConversion(userId: Long, days: Int): SubscriberConversionResponse {
        /*
         * 어떤 업로드가 구독 증가를 보고하는 플랫폼의 것인지 확인한다.
         * `findCrossPlatformDetailMetrics` 는 이미 플랫폼을 함께 주므로 새 쿼리가 없다.
         * 알 수 없는 플랫폼 문자열은 허용 목록에 넣지 않는다 — fail-closed.
         */
        val platformByUploadId = analyticsRepository.findCrossPlatformDetailMetrics(userId, days)
            .filter {
                PlatformMetricAvailability.isAvailable(it.platform, PlatformMetricAvailability.SUBSCRIBER_GAINED)
            }
            .associate { it.videoUploadId to it.platform }
        val reportingUploadIds = platformByUploadId.keys

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(days.toLong())

        /*
         * 기간 · 허용 uploadId · `views > 0` 세 조건을 모두 만족하는 행만 남긴다.
         *
         * `views > 0` 이 분모 조건이다. 조회가 없으면 전환율을 만들 수 없다.
         */
        val measured = analyticsRepository.findAllByUserId(userId).filter {
            it.date in startDate..endDate &&
                it.videoUploadId in reportingUploadIds &&
                it.views > 0
        }

        if (measured.isEmpty()) {
            return SubscriberConversionResponse(
                period = "${days}d",
                totalGained = null,
                data = emptyList(),
                measuredPlatforms = emptyList(),
                unavailableReason = SUBSCRIBER_CONVERSION_UNAVAILABLE,
            )
        }

        // 날짜별 포인트. 유효한 행이 없는 날짜는 **포인트를 만들지 않는다** —
        // 0 포인트를 그리면 그날 전환율이 0 이었다는 관측이 된다.
        val dataPoints = measured.groupBy { it.date }.entries.sortedBy { it.key }.map { (date, records) ->
            val gained = records.sumOf { it.subscriberGained }
            val views = records.sumOf { it.views.toLong() }
            SubscriberConversionPoint(
                date = date.toString(),
                gained = gained,
                views = views,
                // 같은 행에서 나온 값끼리 나눈다. views > 0 이 보장돼 있다.
                conversionRate = Math.round((gained.toDouble() / views * 100) * 1000) / 1000.0,
            )
        }

        return SubscriberConversionResponse(
            period = "${days}d",
            // 유효한 행이 있으면 합이 0 이어도 측정된 사실이다.
            totalGained = measured.sumOf { it.subscriberGained.toLong() },
            data = dataPoints,
            // 실제로 합계에 들어간 행의 플랫폼만 밝힌다. 이론상 가능한 목록이 아니다.
            measuredPlatforms = measured.mapNotNull { platformByUploadId[it.videoUploadId] }
                .distinct()
                .sorted(),
            unavailableReason = null,
        )
    }

    companion object {
        /**
         * 트래픽 소스·인구통계를 낼 수 없을 때의 사유. 화면이 그대로 보여준다.
         *
         * **"아직 데이터가 없음" 과 구분되는 문구여야 한다** — 기다리면 쌓이는 것이
         * 아니라 수집 경로 자체가 없는 상태다. `channel_insights_daily` 를 채우는
         * [AnalyticsRepository.upsertChannelInsights] 는 호출부가 하나도 없고, 어댑터의
         * `PlatformAnalytics` 에도 해당 필드가 없다.
         *
         * 임의의 분포를 지어내지 않는다. 없는 것은 없다고 말한다.
         */
        const val CHANNEL_INSIGHTS_UNAVAILABLE_REASON =
            "트래픽 소스·시청자 인구통계는 현재 플랫폼 분석 연동에서 수집하지 않아 표시할 수 없습니다."

        /**
         * CTR 을 낼 수 없을 때의 사유. 화면이 그대로 보여준다.
         *
         * 숫자가 아니라 **문장**이어야 한다. 0 을 넣으면 "클릭률 0%" 라는 관측 결과가 된다.
         */
        const val CTR_UNAVAILABLE = "노출 수가 수집되지 않아 클릭률을 계산할 수 없습니다"

        /**
         * 구독 전환을 낼 수 없을 때의 사유. 화면이 그대로 보여준다.
         *
         * 숫자가 아니라 **문장**이어야 한다. 0 을 넣으면 "신규 구독 0명" 이라는 관측 결과가 된다.
         */
        const val SUBSCRIBER_CONVERSION_UNAVAILABLE =
            "구독 증가 수가 수집되지 않아 전환율을 계산할 수 없습니다"

        /**
         * 평균 시청 시간을 낼 수 없을 때의 사유. 화면이 그대로 보여준다.
         *
         * 숫자가 아니라 **문장**이어야 한다. 0 을 넣으면 "0초 시청" 이라는 관측 결과가 된다.
         */
        const val WATCH_TIME_UNAVAILABLE =
            "시청 시간이 수집되지 않아 평균 시청 시간을 계산할 수 없습니다"

        /** 최적 시간 추천이 보는 기간. 예전 로직과 같은 30일이다. */
        const val OPTIMAL_TIME_WINDOW_DAYS = 30

        /** 신뢰도 100% 에 도달하는 표본 수. 이 수 이상은 더 올라가지 않는다. */
        const val MAX_CONFIDENCE_SAMPLES = 30

        /**
         * 최적 시간을 추천할 수 없을 때의 사유. 화면이 그대로 보여준다.
         *
         * 숫자가 아니라 **문장**이어야 한다. 슬롯을 지어내면 그 시각이 추천이 된다.
         */
        const val OPTIMAL_TIMES_UNAVAILABLE =
            "게시 시각이 확인된 성과 데이터가 없어 추천 시간을 계산할 수 없습니다"
    }
}
