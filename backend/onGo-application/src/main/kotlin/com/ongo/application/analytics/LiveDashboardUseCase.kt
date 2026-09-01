package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.*
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.application.revenue.RevenueAvailability
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class LiveDashboardUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val liveAlertRepository: LiveAlertRepository,
    private val liveAlertConfigRepository: LiveAlertConfigRepository,
    private val channelRepository: ChannelRepository,
    /** 어떤 플랫폼이 실제로 집계 행을 만들었는지 알아야 지표별 수집 여부를 판정할 수 있다. */
    private val videoUploadRepository: VideoUploadRepository,
    /** 수익은 플랫폼만으로 판정할 수 없다 — `revenue_status` 까지 봐야 한다. */
    private val revenueRepository: RevenueRepository,
) {

    /**
     * 라이브 대시보드 현황 - analytics_daily 기반 지표 + 알림 + 연동 플랫폼.
     * 현재 기간(오늘) vs 이전 기간(어제)을 비교하고, 최근 7일 history를 제공한다.
     */
    fun getLiveState(userId: Long): LiveDashboardStateResponse {
        val today = LocalDate.now()
        val historyFrom = today.minusDays(7)

        /*
         * **`getDailyAggregates` 를 쓰지 않는다 — 그 결과에는 플랫폼이 없다.**
         *
         * `AnalyticsJooqRepository.kt:641` 은 `analytics_daily` 를 **날짜로만** 묶는다.
         * 그래서 `TumblrClient.kt:141` 의 `total_notes`(좋아요+리블로그+답글 총합)가 `VIEWS`
         * 합계에, `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭)이
         * `LIKES`·`SHARES` 합계에 그대로 더해진다.
         *
         * 이건 수집하지 않는 플랫폼의 하드코딩 0 과 다르다. **다른 뜻의 큰 숫자**라
         * 합계 자체를 오염시킨다. 같은 기간을 업로드 단위로 읽어 행마다 플랫폼을 붙인다.
         */
        val channelUploads = videoUploadRepository.findByUserId(userId)
        val rowPlatforms = AnalyticsRowPlatforms.of(channelUploads)
        val rows = analyticsRepository
            .findByVideoUploadIdsAndDateRange(channelUploads.mapNotNull { it.id }, historyFrom, today)
            .values
            .flatten()

        /*
         * 측정된 행이 하나라도 있는가.
         *
         * 행이 하나라도 있으면 그 안의 0 은 **유효한 측정값**이다. 그 경우는 true 다.
         */
        val dataAvailable = rows.isNotEmpty()

        /*
         * **이 사용자가 어떤 플랫폼에 게시했는가 — 행이 아니라 업로드로 판정한다.**
         *
         * "그 지표를 주는 플랫폼이 하나도 없다"(영원히 못 잰다)와 "아직 수집 전이다"
         * (곧 채워진다)는 서로 다른 상태다. 행만 보면 둘이 똑같이 비어 보인다.
         */
        val uploadPlatforms = channelUploads.map { it.platform.name }.toSet()

        /*
         * 수익은 플랫폼만으로 부족하다. YouTube 를 연결했어도 재연동 전이면 못 읽고,
         * 그때의 0 은 "수익 0 원" 이 아니다. 이미 있는 판정을 그대로 쓴다.
         */
        val revenueAvailability = RevenueAvailability.evaluate(
            revenueRepository.getRevenueStatusCounts(userId, historyFrom, today),
        )

        // 6개 지표 카드 생성
        val metrics = buildMetrics(
            rows,
            rowPlatforms,
            today,
            uploadPlatforms,
            revenueAvailability,
        )

        // 알림 목록
        val alerts = liveAlertRepository.findByUserId(userId).map { it.toAlertResponse() }

        // 연동된 활성 플랫폼
        val activePlatforms = channelRepository.findByUserId(userId)
            .filter { it.status == ChannelStatus.ACTIVE }
            .map { it.platform.name.lowercase() }

        return LiveDashboardStateResponse(
            metrics = metrics,
            alerts = alerts,
            activePlatforms = activePlatforms,
            // 갱신된 적 없는 데이터에 갱신 시각을 붙이지 않는다. 클라이언트가
            // "마지막 업데이트: 방금"을 그리면 비어 있는 화면이 최신처럼 보인다.
            lastUpdated = if (dataAvailable) {
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toString()
            } else {
                null
            },
            isConnected = activePlatforms.isNotEmpty(),
            dataAvailable = dataAvailable,
        )
    }

    /**
     * 알림 목록 조회
     */
    fun getAlerts(userId: Long): List<LiveAlertResponse> {
        return liveAlertRepository.findByUserId(userId).map { it.toAlertResponse() }
    }

    /**
     * 알림 읽음 처리
     */
    @Transactional
    fun markAlertRead(userId: Long, alertId: Long) {
        val alert = liveAlertRepository.findById(alertId)
            ?: throw NotFoundException("라이브 알림", alertId)
        if (alert.userId != userId) throw ForbiddenException("해당 알림에 대한 권한이 없습니다")
        liveAlertRepository.markRead(alertId)
    }

    /**
     * 알림 설정 조회
     */
    fun getAlertConfigs(userId: Long): List<LiveAlertConfigResponse> {
        return liveAlertConfigRepository.findByUserId(userId).map { config ->
            LiveAlertConfigResponse(
                id = config.id!!,
                type = config.type,
                enabled = config.enabled,
                threshold = config.threshold,
            )
        }
    }

    /**
     * 알림 설정 수정
     */
    @Transactional
    fun updateAlertConfig(userId: Long, request: UpdateLiveAlertConfigRequest): LiveAlertConfigResponse {
        val existing = liveAlertConfigRepository.findById(request.id)

        val config = if (existing != null) {
            if (existing.userId != userId) throw ForbiddenException("해당 알림 설정에 대한 권한이 없습니다")
            liveAlertConfigRepository.update(
                existing.copy(
                    type = request.type,
                    enabled = request.enabled,
                    threshold = request.threshold,
                )
            )
        } else {
            liveAlertConfigRepository.save(
                LiveAlertConfig(
                    userId = userId,
                    type = request.type,
                    enabled = request.enabled,
                    threshold = request.threshold,
                )
            )
        }

        return LiveAlertConfigResponse(
            id = config.id!!,
            type = config.type,
            enabled = config.enabled,
            threshold = config.threshold,
        )
    }

    /**
     * analytics_daily 기반 업로드 추천 시간
     */
    fun getHeatmapRecommendations(userId: Long): List<HeatmapRecommendationResponse> {
        val heatmapData = analyticsRepository.getHeatmapData(userId)
        if (heatmapData.isEmpty()) return emptyList()

        val dayMapping = mapOf(
            "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3,
            "THU" to 4, "FRI" to 5, "SAT" to 6,
        )
        val dayLabels = mapOf(
            0 to "일요일", 1 to "월요일", 2 to "화요일", 3 to "수요일",
            4 to "목요일", 5 to "금요일", 6 to "토요일",
        )

        val recommendations = mutableListOf<HeatmapRecommendationResponse>()

        for ((dayName, hourMap) in heatmapData) {
            val dayOfWeek = dayMapping[dayName] ?: continue
            for ((hour, views) in hourMap) {
                recommendations.add(
                    HeatmapRecommendationResponse(
                        dayOfWeek = dayOfWeek,
                        hour = hour,
                        score = views.toDouble(),
                        reason = "${dayLabels[dayOfWeek]} ${hour}시에 평균 ${views}회 조회수를 기록했습니다",
                    )
                )
            }
        }

        return recommendations
            .sortedByDescending { it.score }
            .take(10)
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 6개 지표 카드(VIEWS, SUBSCRIBERS, LIKES, COMMENTS, WATCH_TIME, REVENUE) 생성
     */
    private fun buildMetrics(
        rows: List<AnalyticsDaily>,
        rowPlatforms: AnalyticsRowPlatforms,
        today: LocalDate,
        uploadPlatforms: Set<String>,
        revenueAvailability: RevenueAvailability.Result,
    ): List<LiveMetricResponse> {
        data class MetricDef(
            val type: String,
            /** [PlatformMetricAvailability] 의 지표 이름. 이 지표를 주는 플랫폼이 있는지 판정한다. */
            val metric: String,
            val extract: (AnalyticsDaily) -> Long,
        )

        val definitions = listOf(
            MetricDef("VIEWS", PlatformMetricAvailability.VIEWS) { it.views.toLong() },
            MetricDef("SUBSCRIBERS", PlatformMetricAvailability.SUBSCRIBER_GAINED) { it.subscriberGained.toLong() },
            MetricDef("LIKES", PlatformMetricAvailability.LIKES) { it.likes.toLong() },
            MetricDef("COMMENTS", PlatformMetricAvailability.COMMENTS) { it.commentsCount.toLong() },
            MetricDef("WATCH_TIME", PlatformMetricAvailability.WATCH_TIME_SECONDS) { it.watchTimeSeconds },
            MetricDef("REVENUE", PlatformMetricAvailability.REVENUE_MICRO) { it.revenueMicro },
        )

        return definitions.map { def ->
            /*
             * 이 지표를 **물어볼 수 있는 플랫폼이 하나라도 있었는가.**
             *
             * `SUBSCRIBERS`·`WATCH_TIME`·`REVENUE` 는 `YouTubeClient` 만 조회한다. TikTok·
             * Instagram 만 쓰는 크리에이터에게 그 합계 0 은 "오늘 0" 이 아니라 물어볼 곳이
             * 없다는 뜻인데, 예전에는 실제 0 과 구분되지 않았다.
             */
            val reported = uploadPlatforms.any { PlatformMetricAvailability.isAvailable(it, def.metric) }

            // 수익은 여기에 더해 `revenue_status` 판정까지 통과해야 한다.
            val unavailableReason = when {
                !reported -> METRIC_NOT_COLLECTED
                def.type == "REVENUE" && !revenueAvailability.available -> revenueAvailability.reason
                else -> null
            }

            if (unavailableReason != null) {
                return@map LiveMetricResponse(
                    type = def.type,
                    currentValue = null,
                    previousValue = null,
                    changePercent = null,
                    trend = TREND_UNKNOWN,
                    unavailableReason = unavailableReason,
                    // 그릴 수 있는 계열이 없다. 0 선을 그으면 "계속 0 이었다" 로 보인다.
                    history = emptyList(),
                )
            }

            /*
             * **이 지표를 실제로 수집하는 행만 더한다.**
             *
             * 가용성을 지표 단위가 아니라 **행 단위**로 적용하는 지점이다. YouTube 와
             * Tumblr 를 함께 쓰면 `VIEWS` 는 "수집하는 플랫폼 있음"으로 판정되지만,
             * 합계에 Tumblr 의 노트 총합이 섞이면 그 숫자는 조회수가 아니다.
             * 지원하는 행만 남기면 섞임이 사라지고, 남은 행의 0 은 실측 그대로다.
             */
            val measuredRows = rowPlatforms.rowsReporting(rows, def.metric)
                .let { reporting ->
                    // 수익은 행 단위 상태까지 봐야 한다 — `AnalyticsDaily.revenueMicro` 는
                    // `revenueStatus == MEASURED` 일 때만 의미가 있다(도메인 주석).
                    if (def.type == "REVENUE") {
                        reporting.filter { it.revenueStatus == RevenueStatus.MEASURED }
                    } else {
                        reporting
                    }
                }

            /*
             * 게시한 플랫폼은 이 지표를 주지만 기간 내 수집된 행이 아직 없다.
             * 예전에는 여기서 합성 행의 `0` 이 나가 "오늘 0 건"과 구분되지 않았다.
             */
            if (measuredRows.isEmpty()) {
                return@map LiveMetricResponse(
                    type = def.type,
                    currentValue = null,
                    previousValue = null,
                    changePercent = null,
                    trend = TREND_UNKNOWN,
                    unavailableReason = METRIC_NOT_MEASURED_YET,
                    history = emptyList(),
                )
            }

            val byDate = measuredRows.groupBy { it.date }.toSortedMap()
            val measuredDates = byDate.keys.toList()
            fun sumOn(date: LocalDate?): Long? = date?.let { d -> byDate[d]?.sumOf(def.extract) }

            // 현재 값: 오늘 (없으면 측정된 가장 최근 날짜)
            val currentDate = if (today in byDate) today else measuredDates.last()
            val currentValue = sumOn(currentDate)!!
            /*
             * 이전 값: **현재 날짜보다 앞선 가장 최근 측정 날짜.**
             *
             * 예전처럼 `어제` 를 먼저 찾으면 자정을 넘겼을 때 어제가 곧 현재 날짜가 되어
             * 같은 행을 현재와 이전 양쪽에 넣는다. 측정 날짜가 하나뿐이면 비교 대상이
             * 없다 → `null`.
             */
            val previousValue = sumOn(measuredDates.lastOrNull { it < currentDate })

            // 이전 값이 0 이거나 없으면 비율의 기준이 없다 → null. [MetricChange] 참고.
            val changePercent = previousValue?.let { MetricChange.percentChange(it, currentValue) }
            val trend = when {
                // 비교 불가를 STABLE 로 내리면 "변화 없음"이라는 측정 결과가 되어 버린다.
                changePercent == null -> TREND_UNKNOWN
                changePercent > 1.0 -> "UP"
                changePercent < -1.0 -> "DOWN"
                else -> "STABLE"
            }

            // 측정된 날짜만 그린다. 수집하지 않는 플랫폼의 날짜에 0 점을 찍으면
            // 그래프가 "그날 0 이었다"로 읽힌다.
            val history = byDate.map { (date, dayRows) ->
                LiveMetricPointResponse(
                    timestamp = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
                    value = dayRows.sumOf(def.extract),
                )
            }

            LiveMetricResponse(
                type = def.type,
                currentValue = currentValue,
                previousValue = previousValue,
                // null 을 반올림하려다 0 으로 만들면 비교 불가가 "변화 없음"이 된다.
                changePercent = changePercent?.let { Math.round(it * 100) / 100.0 },
                trend = trend,
                history = history,
            )
        }
    }

    companion object {
        /**
         * 비교할 이전 값이 없을 때의 [LiveMetricResponse.trend].
         *
         * `UP`/`DOWN`/`STABLE` 3 값만 있던 계약에 **추가**한 네 번째 값이다. 기존 값의
         * 의미는 하나도 바뀌지 않았고, 이 값은 예전에 `STABLE`(previous=0, current=0) 또는
         * `UP`(previous=0, current>0) 으로 **잘못 표시되던 경우에만** 나온다.
         *
         * 이 API(`GET /api/v1/analytics/live`)에는 현재 프론트엔드 소비자가 없다
         * (`frontend/src` 에서 `analytics/live`·`LiveMetric` 검색 결과 0 건). 그래서 값을
         * 추가해도 깨질 화면이 없고, 계약은 `LiveDashboardUseCaseTest` 가 고정한다.
         *
         * 클라이언트는 이 값을 받으면 증감 배지·색상을 그리지 말아야 한다.
         * [LiveMetricResponse.changePercent] 도 함께 `null` 이다.
         */
        const val TREND_UNKNOWN = "UNKNOWN"

        /** 이 지표를 수집하는 플랫폼에 게시된 기록이 없을 때의 [LiveMetricResponse.unavailableReason]. */
        const val METRIC_NOT_COLLECTED = "이 지표를 수집하는 플랫폼에 게시된 기록이 없습니다"

        /**
         * 수집하는 플랫폼에는 게시했지만 **기간 내 측정 행이 없을 때**.
         *
         * [METRIC_NOT_COLLECTED] 와 구분한다 — 저쪽은 물어볼 곳이 아예 없어 영원히 못 재고,
         * 이쪽은 아직 동기화되지 않았을 뿐 다음 수집에서 채워진다.
         */
        const val METRIC_NOT_MEASURED_YET = "선택한 기간에 수집된 측정 기록이 없습니다"
    }

    /** LiveAlert 도메인 → LiveAlertResponse DTO 변환 */
    private fun LiveAlert.toAlertResponse() = LiveAlertResponse(
        id = id!!,
        type = mapAlertType(type),
        title = extractAlertTitle(message),
        description = message,
        metric = mapAlertMetric(type),
        /*
         * `LiveAlert` 도메인에는 값도 임계값도 없다(`LiveAlert.kt` — type·message·severity 뿐).
         * 예전에는 `0L` 을 하드코딩해 내려보냈고, 화면이 그리면 "조회수 0 이 임계값 0 을
         * 넘었다" 는 말이 된다. 없는 것은 없다고 알린다.
         */
        value = null,
        threshold = null,
        createdAt = createdAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
        read = isRead,
    )

    /** 알림 type → 프론트엔드 alert type 매핑 */
    private fun mapAlertType(type: String): String = when (type.uppercase()) {
        "SPIKE", "VIEWS_SPIKE" -> "SPIKE"
        "DROP", "VIEWS_DROP" -> "DROP"
        "MILESTONE" -> "MILESTONE"
        "VIRAL" -> "VIRAL"
        else -> "SPIKE"
    }

    /** 알림 type → 관련 지표 매핑 */
    private fun mapAlertMetric(type: String): String = when (type.uppercase()) {
        "SPIKE", "VIEWS_SPIKE", "DROP", "VIEWS_DROP", "VIRAL" -> "VIEWS"
        "MILESTONE" -> "SUBSCRIBERS"
        else -> "VIEWS"
    }

    /** 알림 메시지에서 제목 추출 (첫 줄 또는 전체 메시지의 앞 50자) */
    private fun extractAlertTitle(message: String): String {
        val firstLine = message.lineSequence().firstOrNull() ?: message
        return if (firstLine.length > 50) firstLine.take(50) + "..." else firstLine
    }
}
