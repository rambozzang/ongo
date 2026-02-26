package com.ongo.domain.sociallistening

import java.time.LocalDateTime

data class BrandMention(
    val id: Long? = null,
    val userId: Long,
    val platform: String,
    val mentionText: String? = null,
    val authorName: String? = null,
    val authorUrl: String? = null,
    val sentiment: String = "NEUTRAL",
    val reach: Long = 0,
    val sourceUrl: String? = null,
    val mentionedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
