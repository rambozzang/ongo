package com.ongo.domain.brandsafety

import java.time.LocalDateTime

data class BrandSafetyCheck(
    val id: Long = 0,
    val workspaceId: Long,
    val contentTitle: String,
    val contentType: String,
    val platform: String,
    val status: String = "PENDING",
    val overallScore: Int = 0,
    val checkedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
