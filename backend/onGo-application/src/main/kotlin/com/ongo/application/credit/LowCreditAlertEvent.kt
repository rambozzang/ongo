package com.ongo.application.credit

/**
 * 크레딧 잔여 20% 이하 알림 이벤트.
 * CreditScheduler 가 매시간 발행하며, 리스너에서 알림 처리.
 */
data class LowCreditAlertEvent(
    val userId: Long,
    val balance: Int,
    val freeMonthly: Int,
)
