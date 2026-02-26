package com.ongo.domain.sociallistening

import java.time.LocalDateTime

data class KeywordAlert(
    val id: Long? = null,
    val userId: Long,
    val keyword: String,
    val platforms: String = "[]", // JSONB as String
    val enabled: Boolean = true,
    val notifyEmail: Boolean = false,
    val notifyPush: Boolean = true,
    val lastTriggeredAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
