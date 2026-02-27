package com.ongo.application.portfolio.dto

import java.time.LocalDateTime

data class PortfolioResponse(
    val id: Long,
    val userId: Long,
    val displayName: String?,
    val bio: String?,
    val category: String?,
    val profileImageUrl: String?,
    val theme: String,
    val publicSlug: String?,
    val showcaseVideos: String,
    val brandHistory: String,
    val isPublic: Boolean,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreatePortfolioRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val category: String? = null,
    val profileImageUrl: String? = null,
    val theme: String = "default",
    val publicSlug: String? = null,
    val showcaseVideos: String = "[]",
    val brandHistory: String = "[]",
    val isPublic: Boolean = false,
)

data class UpdatePortfolioRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val category: String? = null,
    val profileImageUrl: String? = null,
    val theme: String? = null,
    val publicSlug: String? = null,
    val showcaseVideos: String? = null,
    val brandHistory: String? = null,
    val isPublic: Boolean? = null,
)

data class UpdateProfileRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val category: String? = null,
    val profileImageUrl: String? = null,
)

data class ShowcaseOrderRequest(
    val contentIds: List<Long>,
)

data class AddShowcaseRequest(
    val contentId: Long,
    val title: String? = null,
    val thumbnailUrl: String? = null,
)

data class ShowcaseItemResponse(
    val id: Long,
    val order: Int,
)

data class AddCollaborationRequest(
    val brandName: String,
    val description: String? = null,
    val date: String? = null,
    val logoUrl: String? = null,
)

data class CollaborationResponse(
    val id: Long,
)

data class UpdateSettingsRequest(
    val theme: String? = null,
    val publicSlug: String? = null,
    val isPublic: Boolean? = null,
)
