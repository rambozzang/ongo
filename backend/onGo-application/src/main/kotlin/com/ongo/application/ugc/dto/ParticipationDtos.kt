package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class CreateInviteRequest(
    val expiresInDays: Int? = null,
    val maxUses: Int? = null,
)

data class InviteResponse(
    val id: Long,
    val campaignId: Long,
    /** 원문 토큰은 발급 시 1회만 채워진다. 조회 시에는 null. */
    val token: String?,
    val expiresAt: LocalDateTime?,
    val maxUses: Int?,
    val usedCount: Int,
    val active: Boolean,
)

data class PublicCampaignResponse(
    val campaignId: Long,
    val name: String,
    val description: String?,
    val objective: String,
    val status: String,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val currency: String,
    val fixedRewardPerCreator: Long,
    val playbookTitle: String?,
    val playbookSummary: String?,
    val alreadyApplied: Boolean,
)

data class ApplyRequest(
    val message: String? = null,
    val portfolioUrl: String? = null,
)

data class ApplicationResponse(
    val id: Long,
    val campaignId: Long,
    val creatorId: Long,
    val message: String?,
    val portfolioUrl: String?,
    val status: String,
    val decidedBy: Long?,
    val decidedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class ApplicationListResponse(
    val items: List<ApplicationResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)

data class MyApplicationResponse(
    val application: ApplicationResponse,
    val campaignName: String,
    val campaignStatus: String,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
)

data class MyApplicationListResponse(
    val items: List<MyApplicationResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)
