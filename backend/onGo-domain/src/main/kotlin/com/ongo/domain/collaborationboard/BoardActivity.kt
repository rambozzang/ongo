package com.ongo.domain.collaborationboard

import java.time.LocalDateTime

data class BoardActivity(
    val id: Long = 0,
    val workspaceId: Long,
    val userId: Long,
    val action: String,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val fromColumn: String? = null,
    val toColumn: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
