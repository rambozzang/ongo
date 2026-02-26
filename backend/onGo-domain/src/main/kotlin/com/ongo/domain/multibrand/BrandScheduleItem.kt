package com.ongo.domain.multibrand

import java.time.LocalDateTime

data class BrandScheduleItem(
    val id: Long? = null,
    val userId: Long,
    val brandId: Long,
    val title: String,
    val platform: String,
    val scheduledAt: LocalDateTime,
    val status: String = "DRAFT",
    val assignedTo: String? = null,
    val videoId: String? = null,
    val notes: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
