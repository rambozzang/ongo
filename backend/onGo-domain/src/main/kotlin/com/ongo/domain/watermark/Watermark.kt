package com.ongo.domain.watermark

import java.time.LocalDateTime

data class Watermark(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val imageUrl: String,
    val position: String = "BOTTOM_RIGHT",
    val opacity: Double = 0.8,
    val size: Int = 100,
    val isDefault: Boolean = false,
    val createdAt: LocalDateTime? = null,
)
