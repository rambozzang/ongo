package com.ongo.domain.brandsafety

import java.time.LocalDateTime

data class BrandSafetyRule(
    val id: Long = 0,
    val workspaceId: Long,
    val name: String,
    val category: String,
    val description: String,
    val isEnabled: Boolean = true,
    val severity: String = "MEDIUM",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
