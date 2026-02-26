package com.ongo.domain.collaborationboard

import java.time.LocalDate
import java.time.LocalDateTime

data class BoardTask(
    val id: Long = 0,
    val workspaceId: Long,
    val title: String,
    val description: String? = null,
    val columnType: String = "IDEA",
    val priority: String = "MEDIUM",
    val status: String = "TODO",
    val assigneeId: Long? = null,
    val dueDate: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val attachments: Int = 0,
    val videoId: String? = null,
    val orderIndex: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
