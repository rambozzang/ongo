package com.ongo.application.schedule

import com.ongo.common.enums.PlanType
import com.ongo.common.exception.PlanLimitExceededException
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared plan horizon rule for one-off and recurring schedules. */
object SchedulePlanLimit {
    val KST: ZoneId = ZoneId.of("Asia/Seoul")

    fun validate(planType: PlanType, scheduledAt: LocalDateTime, now: LocalDateTime = LocalDateTime.now(KST)) {
        if (planType == PlanType.FREE) throw PlanLimitExceededException("예약 업로드", 0)
        if (scheduledAt.isAfter(now.plusDays(planType.scheduleDays.toLong()))) {
            throw PlanLimitExceededException("예약 기간", planType.scheduleDays)
        }
    }
}
