package com.ongo.application.template.dto

import java.time.LocalDateTime

data class TemplateResponse(
    val id: Long,
    val name: String,
    val titleTemplate: String?,
    val descriptionTemplate: String?,
    val tags: List<String>,
    val category: String?,
    val platform: String?,
    val usageCount: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class TemplateListResponse(
    val templates: List<TemplateResponse>,
    val totalCount: Int,
)

data class CreateTemplateRequest(
    val name: String,
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val platform: String? = null,
)

data class UpdateTemplateRequest(
    val name: String,
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val platform: String? = null,
)
