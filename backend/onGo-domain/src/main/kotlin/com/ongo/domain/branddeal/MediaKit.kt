package com.ongo.domain.branddeal

import java.time.LocalDateTime

data class MediaKit(
    val id: Long? = null,
    val userId: Long,
    val displayName: String,
    val bio: String? = null,
    val categories: String? = "[]",
    val socialLinks: String? = "{}",
    val statsSnapshot: String? = "{}",
    val rateCard: String? = "{}",
    val isPublic: Boolean = false,
    val slug: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
