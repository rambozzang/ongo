package com.ongo.domain.idea

import java.time.LocalDate
import java.time.LocalDateTime

data class Idea(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val status: String = "BACKLOG",
    val category: String? = null,
    val tags: Array<String> = emptyArray(),
    val priority: String = "MEDIUM",
    val source: String? = null,
    val referenceUrl: String? = null,
    val dueDate: LocalDate? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
