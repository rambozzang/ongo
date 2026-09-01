package com.ongo.application.automation

import com.ongo.application.analytics.AnalyticsRowPlatforms
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.automation.AutomationRuleRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class PerformanceTriggerEvaluator(
    private val automationRuleRepository: AutomationRuleRepository,
    private val analyticsRepository: AnalyticsRepository,
    /** 집계 행의 플랫폼을 알아야 지표별 수집 여부를 판정할 수 있다. */
    private val videoUploadRepository: VideoUploadRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        val PERFORMANCE_TRIGGER_TYPES = setOf("VIEWS_MILESTONE", "VIRAL_DETECTED", "ENGAGEMENT_DROP")
        val DEFAULT_MILESTONES = listOf(1000L, 5000L, 10000L, 100000L)
    }

    @Scheduled(fixedRate = 900_000) // Every 15 minutes
    fun evaluatePerformanceTriggers() {
        log.debug("Performance trigger evaluation started")

        // Find all active rules with performance trigger types
        val allRules = automationRuleRepository.findAll()
        val performanceRules = allRules.filter { it.isActive && it.triggerType in PERFORMANCE_TRIGGER_TYPES }

        if (performanceRules.isEmpty()) return

        val userIds = performanceRules.map { it.userId }.distinct()

        for (userId in userIds) {
            val userRules = performanceRules.filter { it.userId == userId }
            val analytics = analyticsRepository.findDailyAnalyticsByChannelIds(userId, null)
            if (analytics.isEmpty()) continue

            /*
             * **원시 행에는 플랫폼이 없다.** `AnalyticsDaily` 는 `videoUploadId` 만 들고
             * 있어, 그대로 더하면 `TumblrClient.kt:141` 의 `total_notes`(노트 총합)가
             * 조회수로, `PinterestClient.kt:158/160` 의 `SAVE`(저장)·`PIN_CLICK`(클릭)이
             * 참여 수로 섞인다.
             *
             * 이 값들은 **알림을 실제로 발사한다.** 오염되면 도달한 적 없는 조회수
             * 마일스톤을 축하하거나, 재지 않은 참여율 하락을 경고한다.
             */
            val rowPlatforms = AnalyticsRowPlatforms.of(videoUploadRepository.findByUserId(userId))
            val viewRows = rowPlatforms.rowsReporting(analytics, PlatformMetricAvailability.VIEWS)
            // 조회수를 물어볼 수 있는 행이 없으면 어떤 규칙도 판정할 수 없다.
            if (viewRows.isEmpty()) continue

            val recentDate = LocalDate.now().minusDays(7)
            val recentViewRows = viewRows.filter { !it.date.isBefore(recentDate) }
            val avgViews = viewRows.map { it.views.toLong() }.average()
            val totalViews = viewRows.sumOf { it.views.toLong() }

            /*
             * 참여율은 분자와 분모가 **같은 행**에서 나와야 한다. 좋아요·댓글·공유·조회수를
             * 모두 수집하는 행만 쓴다 — 분자에서만 빼고 조회수를 분모에 남기면 참여율이
             * 실제보다 낮아져 없는 하락을 경고한다.
             */
            val engagementRows = rowPlatforms.rowsReporting(
                analytics,
                PlatformMetricAvailability.LIKES,
                PlatformMetricAvailability.COMMENTS,
                PlatformMetricAvailability.SHARES,
                PlatformMetricAvailability.VIEWS,
            )
            val recentEngagementRows = engagementRows.filter { !it.date.isBefore(recentDate) }

            for (rule in userRules) {
                try {
                    // The scheduler runs repeatedly. Keep a triggered rule quiet
                    // for one evaluation window so the same condition cannot
                    // create duplicate notifications every 15 minutes.
                    if (rule.lastTriggeredAt?.isAfter(LocalDateTime.now().minusHours(1)) == true) continue
                    val triggered = when (rule.triggerType) {
                        "VIEWS_MILESTONE" -> evaluateViewsMilestone(rule.triggerConfig, totalViews)
                        "VIRAL_DETECTED" -> evaluateViralDetection(rule.triggerConfig, recentViewRows, avgViews)
                        "ENGAGEMENT_DROP" ->
                            evaluateEngagementDrop(rule.triggerConfig, engagementRows, recentEngagementRows)
                        else -> false
                    }

                    if (triggered) {
                        eventPublisher.publishEvent(
                            PerformanceTriggerFiredEvent(
                                userId = userId,
                                ruleId = rule.id!!,
                                triggerType = rule.triggerType,
                                details = mapOf("ruleName" to rule.name),
                            )
                        )
                        log.info("Performance trigger fired: userId=$userId, ruleId=${rule.id}, type=${rule.triggerType}")
                    }
                } catch (e: Exception) {
                    log.error("Error evaluating rule ${rule.id} for user $userId", e)
                }
            }
        }

        log.debug("Performance trigger evaluation completed")
    }

    private fun evaluateViewsMilestone(config: Map<String, Any?>, totalViews: Long): Boolean {
        @Suppress("UNCHECKED_CAST")
        val milestones = (config["milestones"] as? List<Number>)?.map { it.toLong() }
            ?: DEFAULT_MILESTONES
        return milestones.any { milestone -> totalViews >= milestone }
    }

    private fun evaluateViralDetection(
        config: Map<String, Any?>,
        recentAnalytics: List<com.ongo.domain.analytics.AnalyticsDaily>,
        avgViews: Double,
    ): Boolean {
        val multiplier = (config["multiplier"] as? Number)?.toDouble() ?: 3.0
        if (avgViews <= 0) return false
        val recentMaxViews = recentAnalytics.maxOfOrNull { it.views } ?: 0
        return recentMaxViews > avgViews * multiplier
    }

    private fun evaluateEngagementDrop(
        config: Map<String, Any?>,
        allAnalytics: List<com.ongo.domain.analytics.AnalyticsDaily>,
        recentAnalytics: List<com.ongo.domain.analytics.AnalyticsDaily>,
    ): Boolean {
        val dropPercent = (config["dropPercent"] as? Number)?.toInt() ?: 50
        if (allAnalytics.isEmpty() || recentAnalytics.isEmpty()) return false

        /**
         * **분모가 없으면 `null`.** 예전에는 `0.0` 을 돌려줬는데, 최근 조회수가 0 이면
         * `recentRate = 0.0` 이 되어 `dropRatio` 가 **100%** 로 계산됐다. 참여율을 잰
         * 적도 없는데 "참여율 급락" 알림이 나갔다.
         */
        fun engagementRate(records: List<com.ongo.domain.analytics.AnalyticsDaily>): Double? {
            val totalViews = records.sumOf { it.views.toLong() }
            if (totalViews == 0L) return null
            val totalEngagement = records.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
            return totalEngagement.toDouble() / totalViews * 100.0
        }

        // 두 구간 모두 실제로 측정돼야 하락을 말할 수 있다.
        val overallRate = engagementRate(allAnalytics) ?: return false
        val recentRate = engagementRate(recentAnalytics) ?: return false

        if (overallRate <= 0) return false
        val dropRatio = ((overallRate - recentRate) / overallRate) * 100
        return dropRatio >= dropPercent
    }
}

data class PerformanceTriggerFiredEvent(
    val userId: Long,
    val ruleId: Long,
    val triggerType: String,
    val details: Map<String, Any?> = emptyMap(),
)
