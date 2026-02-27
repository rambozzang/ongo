package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class LiveDashboardUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val liveAlertRepository: LiveAlertRepository,
    private val liveAlertConfigRepository: LiveAlertConfigRepository,
) {

    /**
     * 라이브 대시보드 현황 - 현재 활성 analytics_daily 기반 + 채널 정보
     */
    fun getLiveState(userId: Long): LiveDashboardStateResponse {
        val today = LocalDate.now()
        val allAnalytics = analyticsRepository.findAllByUserId(userId)
        val todayData = allAnalytics.filter { it.date == today }

        val totalViewers = todayData.sumOf { it.views }
        val peakViewers = todayData.maxOfOrNull { it.views } ?: 0
        val avgWatchTime = if (todayData.isNotEmpty()) {
            (todayData.sumOf { it.avgViewDurationSeconds } / todayData.size)
        } else 0

        // 플랫폼별 현황은 analytics_daily에서 직접 추출 불가하므로 채널 인사이트 기반
        val platformStates = todayData.groupBy { "ALL" }.map { (_, records) ->
            LivePlatformState(
                platform = "ALL",
                viewers = records.sumOf { it.views },
                isLive = records.isNotEmpty(),
            )
        }

        return LiveDashboardStateResponse(
            totalViewers = totalViewers,
            activeStreams = if (todayData.isNotEmpty()) 1 else 0,
            peakViewers = peakViewers,
            avgWatchTime = avgWatchTime,
            platforms = platformStates,
        )
    }

    /**
     * 미읽은 알림 목록
     */
    fun getAlerts(userId: Long): List<LiveAlertResponse> {
        return liveAlertRepository.findByUserId(userId).map { alert ->
            LiveAlertResponse(
                id = alert.id!!,
                type = alert.type,
                message = alert.message,
                severity = alert.severity,
                isRead = alert.isRead,
                createdAt = alert.createdAt,
            )
        }
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
}
