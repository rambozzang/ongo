package com.ongo.domain.hashtaganalytics

import java.time.LocalDateTime

data class HashtagGroup(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val hashtags: List<String> = emptyList(),
    val platform: String,
    val usageCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
