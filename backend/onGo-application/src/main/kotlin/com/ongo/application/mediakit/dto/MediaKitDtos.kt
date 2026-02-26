package com.ongo.application.mediakit.dto

import java.time.LocalDateTime

data class MediaKitGenerateRequest(
    val templateStyle: String = "MODERN",
    val includeRateCards: Boolean = true,
    val includeCampaigns: Boolean = true,
    val customBio: String? = null,
)

data class UpdateMediaKitRequest(
    val title: String? = null,
    val bio: String? = null,
    val rateCards: String? = null,
    val contactEmail: String? = null,
)

data class MediaKitResponse(
    val id: Long,
    val title: String,
    val templateStyle: String,
    val bio: String?,
    val profileImageUrl: String?,
    val platforms: String,
    val demographics: String,
    val topContent: String,
    val campaignResults: String,
    val rateCards: String,
    val contactEmail: String?,
    val status: String,
    val publishedUrl: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class MediaKitTemplateResponse(
    val id: Long,
    val name: String,
    val style: String,
    val previewUrl: String?,
)
