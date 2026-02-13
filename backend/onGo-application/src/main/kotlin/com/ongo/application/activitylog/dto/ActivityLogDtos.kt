package com.ongo.application.activitylog.dto

import java.time.LocalDateTime

data class ActivityLogResponse(
    val id: Long,
    val userId: Long,
    val action: String,
    val entityType: String?,
    val entityId: Long?,
    val details: String,
    val ipAddress: String?,
    val createdAt: LocalDateTime?,
)

data class ActivityLogListResponse(
    val logs: List<ActivityLogResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)
