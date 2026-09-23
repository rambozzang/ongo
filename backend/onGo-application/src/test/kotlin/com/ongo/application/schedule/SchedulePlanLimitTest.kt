package com.ongo.application.schedule

import com.ongo.common.enums.PlanType
import com.ongo.common.exception.PlanLimitExceededException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SchedulePlanLimitTest {
    private val now = LocalDateTime.of(2026, 9, 23, 12, 0)

    @Test
    fun `one-off and recurring plans share the exact schedule horizon rule`() {
        assertDoesNotThrow { SchedulePlanLimit.validate(PlanType.STARTER, now.plusDays(7), now) }
        assertThrows(PlanLimitExceededException::class.java) {
            SchedulePlanLimit.validate(PlanType.STARTER, now.plusDays(7).plusNanos(1), now)
        }
        assertThrows(PlanLimitExceededException::class.java) {
            SchedulePlanLimit.validate(PlanType.FREE, now, now)
        }
    }
}
