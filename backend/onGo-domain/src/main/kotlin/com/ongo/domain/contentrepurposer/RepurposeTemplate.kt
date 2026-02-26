package com.ongo.domain.contentrepurposer

import java.time.LocalDateTime

data class RepurposeTemplate(
    val id: Long = 0,
    val workspaceId: Long? = null,
    val name: String,
    val sourcePlatform: String,
    val targetPlatform: String,
    val targetFormat: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
