package com.ongo.domain.analytics

import java.time.LocalDateTime

data class LiveAlert(
    val id: Long? = null,
    val userId: Long,
    val type: String,
    val message: String,
    val severity: String = "INFO",
    val isRead: Boolean = false,
    val createdAt: LocalDateTime? = null,
)
