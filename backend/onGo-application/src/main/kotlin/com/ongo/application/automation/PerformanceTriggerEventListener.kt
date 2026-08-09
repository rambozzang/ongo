package com.ongo.application.automation

import com.ongo.common.enums.NotificationType
import com.ongo.application.credit.LowCreditAlertEvent
import com.ongo.application.video.UploadCompletedEvent
import com.ongo.domain.automation.AutomationRuleRepository
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.platformautomation.AutomationLog
import com.ongo.domain.platformautomation.AutomationLogRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Completes the performance-trigger flow. The evaluator only detects an event;
 * this listener is the durable side-effect boundary that records the outcome
 * and exposes it through the notification center and automation log tab.
 */
@Component
class PerformanceTriggerEventListener(
    private val ruleRepository: AutomationRuleRepository,
    private val logRepository: AutomationLogRepository,
    private val notificationRepository: NotificationRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    @Transactional
    fun onPerformanceTrigger(event: PerformanceTriggerFiredEvent) {
        executeRule(event.userId, event.ruleId, event.triggerType)
    }

    @EventListener
    @Transactional
    fun onUploadCompleted(event: UploadCompletedEvent) {
        if (!event.success) return
        executeMatchingRules(event.userId, "VIDEO_UPLOADED")
    }

    @EventListener
    @Transactional
    fun onLowCredit(event: LowCreditAlertEvent) {
        executeMatchingRules(event.userId, "CREDIT_LOW")
    }

    private fun executeMatchingRules(userId: Long, triggerType: String) {
        val cutoff = LocalDateTime.now().minusHours(1)
        ruleRepository.findAll()
            .filter { it.userId == userId && it.isActive && it.triggerType == triggerType }
            .filter { it.lastTriggeredAt?.isAfter(cutoff) != true }
            .forEach { rule -> executeRule(userId, rule.id!!, triggerType) }
    }

    private fun executeRule(userId: Long, ruleId: Long, triggerType: String) {
        val rule = ruleRepository.findById(ruleId)
        if (rule == null || rule.userId != userId || !rule.isActive) {
            logger.warn("Ignoring automation event for inactive or foreign rule: ruleId={}, userId={}", ruleId, userId)
            return
        }

        val now = LocalDateTime.now()
        val result = runCatching {
            when (rule.actionType) {
                "SEND_NOTIFICATION" -> {
                    notificationRepository.save(
                        Notification(
                            userId = userId,
                            type = NotificationType.SYSTEM,
                            title = "자동화 조건 충족",
                            message = "${rule.name}: $triggerType 조건이 충족되었습니다.",
                            referenceType = "AUTOMATION_RULE",
                            referenceId = rule.id,
                        ),
                    )
                    "알림을 전송했습니다"
                }
                else -> throw IllegalStateException("현재 트리거에서 지원하지 않는 액션입니다: ${rule.actionType}")
            }
        }

        val succeeded = result.isSuccess
        val message = result.getOrElse { it.message ?: "자동화 실행에 실패했습니다" }
        logRepository.save(
            AutomationLog(
                ruleId = rule.id!!,
                ruleName = rule.name,
                status = if (succeeded) "SUCCESS" else "FAILED",
                message = message,
                executedAt = now,
            ),
        )
        ruleRepository.update(rule.copy(
            executionCount = rule.executionCount + 1,
            lastTriggeredAt = now,
        ))
    }
}
