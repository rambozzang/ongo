package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.*
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
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
) {

    /**
     * 라이브 대시보드 현황 - analytics_daily 기반 지표 + 알림 + 연동 플랫폼.
     * 현재 기간(오늘) vs 이전 기간(어제)을 비교하고, 최근 7일 history를 제공한다.
     */
    fun getLiveState(userId: Long): LiveDashboardStateResponse {
        val today = LocalDate.now()
        val historyFrom = today.minusDays(7)

        // 최근 7일 일별 집계 (히스토리 + 현재/이전 비교용)
        val dailyAggregates = analyticsRepository.getDailyAggregates(userId, historyFrom, today)
        val aggregateByDate = dailyAggregates.associateBy { it.date }

        // 현재 값: 오늘 데이터 (없으면 가장 최근 날짜)
        val currentAggregate = aggregateByDate[today]
            ?: dailyAggregates.lastOrNull()
            ?: DailyAggregate(today, 0, 0, 0, 0, 0, 0, 0)

        // 이전 값: 어제 또는 그 이전 날짜
        val yesterday = today.minusDays(1)
        val previousAggregate = aggregateByDate[yesterday]
            ?: dailyAggregates.dropLast(1).lastOrNull()
            ?: DailyAggregate(yesterday, 0, 0, 0, 0, 0, 0, 0)

        // 6개 지표 카드 생성
        val metrics = buildMetrics(currentAggregate, previousAggregate, dailyAggregates)

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
            lastUpdated = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toString(),
            isConnected = activePlatforms.isNotEmpty(),
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
        current: DailyAggregate,
        previous: DailyAggregate,
        dailyAggregates: List<DailyAggregate>,
    ): List<LiveMetricResponse> {
        data class MetricDef(
            val type: String,
            val extract: (DailyAggregate) -> Long,
        )

        val definitions = listOf(
            MetricDef("VIEWS") { it.views },
            MetricDef("SUBSCRIBERS") { it.subscriberGained },
            MetricDef("LIKES") { it.likes },
            MetricDef("COMMENTS") { it.comments },
            MetricDef("WATCH_TIME") { it.watchTimeSeconds },
            MetricDef("REVENUE") { it.revenueMicro },
        )

        return definitions.map { def ->
            val currentValue = def.extract(current)
            val previousValue = def.extract(previous)
            val changePercent = calculateChangePercent(previousValue, currentValue)
            val trend = when {
                changePercent > 1.0 -> "UP"
                changePercent < -1.0 -> "DOWN"
                else -> "STABLE"
            }

            val history = dailyAggregates.map { agg ->
                LiveMetricPointResponse(
                    timestamp = agg.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString(),
                    value = def.extract(agg),
                )
            }

            LiveMetricResponse(
                type = def.type,
                currentValue = currentValue,
                previousValue = previousValue,
                changePercent = Math.round(changePercent * 100) / 100.0,
                trend = trend,
                history = history,
            )
        }
    }

    private fun calculateChangePercent(previous: Long, current: Long): Double =
        if (previous == 0L) {
            if (current > 0) 100.0 else 0.0
        } else {
            ((current - previous).toDouble() / previous.toDouble()) * 100.0
        }

    /** LiveAlert 도메인 → LiveAlertResponse DTO 변환 */
    private fun LiveAlert.toAlertResponse() = LiveAlertResponse(
        id = id!!,
        type = mapAlertType(type),
        title = extractAlertTitle(message),
        description = message,
        metric = mapAlertMetric(type),
        value = 0L,
        threshold = 0L,
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
