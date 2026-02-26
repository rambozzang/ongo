package com.ongo.domain.contentrights

import java.time.LocalDateTime

data class RightsAlert(
    val id: Long? = null,
    val userId: Long,
    val contentRightId: Long,
    val assetName: String,
    val assetType: String,
    val message: String,
    val severity: String = "INFO",
    val daysUntilExpiry: Int = 0,
    val isRead: Boolean = false,
    val createdAt: LocalDateTime? = null,
)
