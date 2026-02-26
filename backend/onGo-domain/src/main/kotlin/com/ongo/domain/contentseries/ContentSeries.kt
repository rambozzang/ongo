package com.ongo.domain.contentseries

import java.time.LocalDateTime

data class ContentSeries(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val status: String = "ACTIVE",
    val platform: String = "YOUTUBE",
    val frequency: String = "WEEKLY",
    val customFrequencyDays: Int? = null,
    val tags: String = "[]",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
