package com.ongo.domain.template

import java.time.LocalDateTime

data class Template(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val titleTemplate: String? = null,
    val descriptionTemplate: String? = null,
    val tags: List<String> = emptyList(),
    val category: String? = null,
    val platform: String? = null,
    val usageCount: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
