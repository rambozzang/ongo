package com.ongo.application.idea.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class IdeaResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val status: String,
    val category: String?,
    val tags: List<String>,
    val priority: String,
    val source: String?,
    val referenceUrl: String?,
    val dueDate: LocalDate?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateIdeaRequest(
    val title: String,
    val description: String? = null,
    val status: String = "BACKLOG",
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val priority: String = "MEDIUM",
    val source: String? = null,
    val referenceUrl: String? = null,
    val dueDate: LocalDate? = null,
)

data class UpdateIdeaRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val category: String? = null,
    val tags: List<String>? = null,
    val priority: String? = null,
    val source: String? = null,
    val referenceUrl: String? = null,
    val dueDate: LocalDate? = null,
)

data class ChangeIdeaStatusRequest(
    val status: String,
)
