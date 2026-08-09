package com.ongo.application.automation

import com.ongo.application.video.UploadCompletedEvent
import com.ongo.common.enums.NotificationType
import com.ongo.domain.automation.AutomationRule
import com.ongo.domain.automation.AutomationRuleRepository
import com.ongo.domain.notification.NotificationRepository
import com.ongo.domain.platformautomation.AutomationLogRepository
import io.mockk.*
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PerformanceTriggerEventListenerTest {
    private val rules = mockk<AutomationRuleRepository>(relaxed = true)
    private val logs = mockk<AutomationLogRepository>(relaxed = true)
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private lateinit var listener: PerformanceTriggerEventListener

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        listener = PerformanceTriggerEventListener(rules, logs, notifications)
    }

    @Test
    fun `performance trigger sends a notification and records success`() {
        val rule = rule(actionType = "SEND_NOTIFICATION")
        every { rules.findById(7L) } returns rule

        listener.onPerformanceTrigger(PerformanceTriggerFiredEvent(11L, 7L, "VIEWS_MILESTONE"))

        verify {
            notifications.save(match {
                it.userId == 11L && it.type == NotificationType.SYSTEM && it.referenceId == 7L
            })
            logs.save(match { it.ruleId == 7L && it.status == "SUCCESS" })
            rules.update(match { it.id == 7L && it.executionCount == 1 })
        }
    }

    @Test
    fun `upload completion evaluates matching user rules`() {
        val rule = rule(id = 9L, userId = 11L, triggerType = "VIDEO_UPLOADED")
        every { rules.findAll() } returns listOf(rule)
        every { rules.findById(9L) } returns rule

        listener.onUploadCompleted(UploadCompletedEvent(1L, 11L, com.ongo.common.enums.Platform.YOUTUBE, true))

        verify { notifications.save(any()) }
        verify { logs.save(match { it.ruleId == 9L && it.status == "SUCCESS" }) }
    }

    @Test
    fun `unsupported action is visible as a failed execution`() {
        val rule = rule(actionType = "AUTO_PUBLISH")
        every { rules.findById(7L) } returns rule

        listener.onPerformanceTrigger(PerformanceTriggerFiredEvent(11L, 7L, "VIEWS_MILESTONE"))

        verify(exactly = 0) { notifications.save(any()) }
        val captured = slot<com.ongo.domain.platformautomation.AutomationLog>()
        verify { logs.save(capture(captured)) }
        assertEquals("FAILED", captured.captured.status)
    }

    private fun rule(
        id: Long = 7L,
        userId: Long = 11L,
        triggerType: String = "VIEWS_MILESTONE",
        actionType: String = "SEND_NOTIFICATION",
    ) = AutomationRule(
        id = id,
        userId = userId,
        name = "테스트 자동화",
        triggerType = triggerType,
        actionType = actionType,
        isActive = true,
    )
}
