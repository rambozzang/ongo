package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class SubmissionAssetDto(
    val assetType: String,
    val resourceType: String? = null,
    val resourceId: Long? = null,
    val externalUrl: String? = null,
)

data class CreateSubmissionRequest(
    val caption: String? = null,
    val assets: List<SubmissionAssetDto> = emptyList(),
)

data class SubmissionResponse(
    val id: Long,
    val campaignId: Long,
    val creatorId: Long,
    val revision: Int,
    val caption: String?,
    val status: String,
    val submittedAt: LocalDateTime?,
    val approvedAt: LocalDateTime?,
    val assets: List<SubmissionAssetDto>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class SubmissionListResponse(
    val items: List<SubmissionResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)

data class ReviewResponse(
    val id: Long,
    val reviewerId: Long,
    val decision: String,
    val comment: String?,
    val createdAt: LocalDateTime?,
)

data class SubmissionDetailResponse(
    val submission: SubmissionResponse,
    val reviews: List<ReviewResponse>,
)

/** request-changes에는 사유가 필수, approve에는 선택. */
data class ReviewDecisionRequest(
    val comment: String? = null,
)
