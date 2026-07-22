package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class PublishRequest(
    val platforms: List<String>,
)

data class RegisterExternalPostRequest(
    val platform: String,
    val externalPostUrl: String,
    val platformPostId: String? = null,
)

data class CampaignPostResponse(
    val id: Long,
    val campaignId: Long,
    val submissionId: Long,
    val creatorId: Long,
    val platform: String,
    val postType: String,
    val videoUploadId: Long?,
    val externalPostUrl: String?,
    val platformPostId: String?,
    val status: String,
    val errorMessage: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CampaignPostListResponse(
    val items: List<CampaignPostResponse>,
)
