package com.ongo.application.notification.dto

import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val referenceType: String?,
    val referenceId: Long?,
    val createdAt: LocalDateTime?,
)

data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val page: Int,
    val size: Int,
)

data class UnreadCountResponse(
    val count: Int,
)
