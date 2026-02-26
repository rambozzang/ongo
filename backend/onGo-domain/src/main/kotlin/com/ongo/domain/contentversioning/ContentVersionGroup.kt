package com.ongo.domain.contentversioning

import java.time.LocalDateTime

data class ContentVersionGroup(
    val id: Long = 0,
    val workspaceId: Long,
    val contentId: Long,
    val contentTitle: String,
    val platform: String,
    val totalVersions: Int = 0,
    val latestVersion: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
