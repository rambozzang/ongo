package com.ongo.domain.mediakit

import java.time.LocalDateTime

data class MediaKit(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val templateStyle: String = "MODERN",
    val bio: String? = null,
    val profileImageUrl: String? = null,
    val platforms: String = "[]",
    val demographics: String = "[]",
    val topContent: String = "[]",
    val campaignResults: String = "[]",
    val rateCards: String = "[]",
    val contactEmail: String? = null,
    val status: String = "DRAFT",
    val publishedUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
