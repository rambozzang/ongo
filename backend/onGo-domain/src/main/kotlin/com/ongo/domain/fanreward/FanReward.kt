package com.ongo.domain.fanreward

import java.time.LocalDateTime

data class FanReward(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val description: String? = null,
    val pointsCost: Int = 0,
    val category: String = "BADGE",
    val isActive: Boolean = true,
    val claimedCount: Int = 0,
    val maxClaims: Int? = null,
    val imageUrl: String? = null,
    val createdAt: LocalDateTime? = null,
)
