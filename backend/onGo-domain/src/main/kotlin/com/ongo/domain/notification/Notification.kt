package com.ongo.domain.notification

import com.ongo.common.enums.NotificationType
import java.time.LocalDateTime

data class Notification(
    val id: Long? = null,
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val referenceType: String? = null,
    val referenceId: Long? = null,
    val createdAt: LocalDateTime? = null,
)
