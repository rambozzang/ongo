package com.ongo.domain.revenuesplit

import java.time.LocalDateTime

data class RevenueSplit(
    val id: Long = 0,
    val workspaceId: Long,
    val title: String,
    val description: String? = null,
    val totalAmount: Long = 0,
    val currency: String = "KRW",
    val status: String = "DRAFT",
    val period: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
