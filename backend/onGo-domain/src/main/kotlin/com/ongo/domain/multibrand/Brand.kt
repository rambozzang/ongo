package com.ongo.domain.multibrand

import java.time.LocalDateTime

data class Brand(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val logoUrl: String? = null,
    val color: String = "BLUE",
    val category: String? = null,
    val assignedEditors: String = "[]",
    val isActive: Boolean = true,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
