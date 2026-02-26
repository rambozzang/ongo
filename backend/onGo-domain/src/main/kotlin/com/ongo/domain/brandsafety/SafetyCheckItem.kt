package com.ongo.domain.brandsafety

import java.time.LocalDateTime

data class SafetyCheckItem(
    val id: Long = 0,
    val checkId: Long,
    val category: String,
    val name: String,
    val status: String = "PASS",
    val severity: String = "LOW",
    val description: String,
    val recommendation: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
