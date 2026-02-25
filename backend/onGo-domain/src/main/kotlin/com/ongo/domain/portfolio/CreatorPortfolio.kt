package com.ongo.domain.portfolio

import java.time.LocalDateTime

data class CreatorPortfolio(
    val id: Long? = null,
    val userId: Long,
    val displayName: String? = null,
    val bio: String? = null,
    val category: String? = null,
    val profileImageUrl: String? = null,
    val theme: String = "default",
    val publicSlug: String? = null,
    val showcaseVideos: String = "[]", // JSONB as String
    val brandHistory: String = "[]", // JSONB as String
    val isPublic: Boolean = false,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
