package com.ongo.application.audience.dto

data class AudienceProfileResponse(
    val id: Long,
    val authorName: String,
    val platform: String?,
    val avatarUrl: String?,
    val engagementScore: Double,
    val tags: List<String>,
    val totalInteractions: Int,
    val positiveRatio: Double,
    val firstSeenAt: String?,
    val lastSeenAt: String?,
)

data class AudienceSegmentResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val conditions: String,
    val autoUpdate: Boolean,
    val memberCount: Int,
    val createdAt: String?,
)

data class CreateSegmentRequest(
    val name: String,
    val description: String? = null,
    val conditions: String = "{}",
    val autoUpdate: Boolean = true,
)

data class UpdateSegmentRequest(
    val name: String? = null,
    val description: String? = null,
    val conditions: String? = null,
)

data class UpdateProfileTagsRequest(
    val tags: List<String>,
)
