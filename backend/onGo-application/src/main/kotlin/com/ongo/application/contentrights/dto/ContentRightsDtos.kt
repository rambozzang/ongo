package com.ongo.application.contentrights.dto

import java.time.LocalDateTime

data class CreateRightRequest(
    val videoId: Long? = null,
    val videoTitle: String? = null,
    val assetName: String,
    val assetType: String = "MUSIC",
    val licenseType: String = "FREE",
    val source: String? = null,
    val licenseUrl: String? = null,
    val expiresAt: LocalDateTime? = null,
    val cost: Long = 0,
    val currency: String = "KRW",
    val notes: String? = null,
)

data class UpdateRightRequest(
    val assetName: String? = null,
    val licenseType: String? = null,
    val licenseStatus: String? = null,
    val expiresAt: LocalDateTime? = null,
    val notes: String? = null,
)

data class ContentRightResponse(
    val id: Long,
    val videoId: Long?,
    val videoTitle: String?,
    val assetName: String,
    val assetType: String,
    val licenseType: String,
    val licenseStatus: String,
    val source: String?,
    val licenseUrl: String?,
    val expiresAt: LocalDateTime?,
    val purchasedAt: LocalDateTime?,
    val cost: Long,
    val currency: String,
    val notes: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class RightsAlertResponse(
    val id: Long,
    val contentRightId: Long,
    val assetName: String,
    val assetType: String,
    val message: String,
    val severity: String,
    val daysUntilExpiry: Int,
    val isRead: Boolean,
    val createdAt: LocalDateTime?,
)

data class RightsSummaryResponse(
    val totalAssets: Int,
    val activeCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val totalCost: Long,
    val byType: String,
    val byLicense: String,
)

data class AlternativeAssetResponse(
    val id: Long,
    val name: String,
    val type: String,
    val source: String,
    val licenseType: String,
    val previewUrl: String?,
    val cost: Long,
    val currency: String,
)
