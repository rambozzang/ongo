package com.ongo.application.automation

import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.automation.AutomationRuleRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PerformanceTriggerEvaluator(
    private val automationRuleRepository: AutomationRuleRepository,
    private val analyticsRepository: AnalyticsRepository,
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

            val recentDate = LocalDate.now().minusDays(7)
            val recentAnalytics = analytics.filter { !it.date.isBefore(recentDate) }
            val avgViews = analytics.map { it.views.toLong() }.average()
            val totalViews = analytics.sumOf { it.views.toLong() }

            for (rule in userRules) {
                try {
                    val triggered = when (rule.triggerType) {
                        "VIEWS_MILESTONE" -> evaluateViewsMilestone(rule.triggerConfig, totalViews)
                        "VIRAL_DETECTED" -> evaluateViralDetection(rule.triggerConfig, recentAnalytics, avgViews)
                        "ENGAGEMENT_DROP" -> evaluateEngagementDrop(rule.triggerConfig, analytics, recentAnalytics)
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

        fun engagementRate(records: List<com.ongo.domain.analytics.AnalyticsDaily>): Double {
            val totalViews = records.sumOf { it.views.toLong() }
            if (totalViews == 0L) return 0.0
            val totalEngagement = records.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
            return totalEngagement.toDouble() / totalViews * 100.0
        }

        val overallRate = engagementRate(allAnalytics)
        val recentRate = engagementRate(recentAnalytics)

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
