package com.ongo.domain.audience

import java.time.LocalDateTime

data class AudienceProfile(
    val id: Long? = null,
    val userId: Long,
    val authorName: String,
    val platform: String? = null,
    val avatarUrl: String? = null,
    val engagementScore: Double = 0.0,
    val tags: String? = "[]",
    val totalInteractions: Int = 0,
    val positiveRatio: Double = 0.0,
    val firstSeenAt: LocalDateTime? = null,
    val lastSeenAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
