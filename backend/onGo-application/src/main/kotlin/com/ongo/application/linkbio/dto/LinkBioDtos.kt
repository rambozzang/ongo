package com.ongo.application.linkbio.dto

import java.time.LocalDateTime

data class LinkBioPageResponse(
    val id: Long,
    val slug: String,
    val title: String?,
    val bio: String?,
    val avatarUrl: String?,
    val theme: String,
    val backgroundColor: String,
    val textColor: String,
    val isPublished: Boolean,
    val viewCount: Long,
    val links: List<LinkBioLinkResponse>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class LinkBioLinkResponse(
    val id: Long,
    val title: String,
    val url: String,
    val icon: String?,
    val sortOrder: Int,
    val clickCount: Long,
    val isActive: Boolean,
    val createdAt: LocalDateTime?,
)

data class CreatePageRequest(
    val slug: String,
    val title: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val theme: String = "default",
    val backgroundColor: String = "#ffffff",
    val textColor: String = "#000000",
)

data class UpdatePageRequest(
    val slug: String? = null,
    val title: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val theme: String? = null,
    val backgroundColor: String? = null,
    val textColor: String? = null,
)

data class UpdateLinksRequest(
    val links: List<LinkItem>,
)

data class LinkItem(
    val id: Long? = null,
    val title: String,
    val url: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

data class PublishRequest(
    val isPublished: Boolean,
)

data class LinkBioPublicResponse(
    val slug: String,
    val title: String?,
    val bio: String?,
    val avatarUrl: String?,
    val theme: String,
    val backgroundColor: String,
    val textColor: String,
    val links: List<PublicLinkResponse>,
)

data class PublicLinkResponse(
    val id: Long,
    val title: String,
    val url: String,
    val icon: String?,
    val sortOrder: Int,
)

data class LinkBioAnalyticsResponse(
    val viewCount: Long,
    val links: List<LinkClickAnalytics>,
)

data class LinkClickAnalytics(
    val id: Long,
    val title: String,
    val clickCount: Long,
)
