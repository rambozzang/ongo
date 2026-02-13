package com.ongo.domain.credit

import java.time.LocalDate
import java.time.LocalDateTime

data class AiCredit(
    val id: Long? = null,
    val userId: Long,
    val balance: Int = 0,
    val freeMonthly: Int = 30,
    val freeRemaining: Int = 30,
    val freeResetDate: LocalDate,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
