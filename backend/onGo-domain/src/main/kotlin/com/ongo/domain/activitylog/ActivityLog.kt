package com.ongo.domain.activitylog

import java.time.LocalDateTime

data class ActivityLog(
    val id: Long? = null,
    val userId: Long,
    val action: String,
    val entityType: String? = null,
    val entityId: Long? = null,
    val details: String = "{}",
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: LocalDateTime? = null,
)
