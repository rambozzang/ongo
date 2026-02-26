package com.ongo.domain.fanfunding

import java.time.LocalDateTime

data class FundingGoal(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val targetAmount: Long = 0,
    val currentAmount: Long = 0,
    val deadline: LocalDateTime? = null,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
