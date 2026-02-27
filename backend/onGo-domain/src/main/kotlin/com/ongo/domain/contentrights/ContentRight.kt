package com.ongo.domain.contentrights

import java.time.LocalDateTime

data class ContentRight(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long? = null,
    val videoTitle: String? = null,
    val assetName: String,
    val assetType: String = "MUSIC",
    val licenseType: String = "FREE",
    val licenseStatus: String = "ACTIVE",
    val source: String? = null,
    val licenseUrl: String? = null,
    val expiresAt: LocalDateTime? = null,
    val purchasedAt: LocalDateTime? = null,
    val cost: Long = 0,
    val currency: String = "KRW",
    val notes: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
