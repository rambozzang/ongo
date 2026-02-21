package com.ongo.domain.workspace

import java.time.LocalDateTime

data class Workspace(
    val id: Long? = null,
    val ownerId: Long,
    val name: String,
    val slug: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
