package com.ongo.domain.contentrepurposer

import java.time.LocalDateTime

data class RepurposeJob(
    val id: Long = 0,
    val workspaceId: Long,
    val originalTitle: String,
    val originalPlatform: String,
    val targetPlatform: String,
    val targetFormat: String,
    val status: String = "PENDING",
    val progress: Int = 0,
    val outputUrl: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
