package com.ongo.application.ugc.dto

import java.time.LocalDateTime

data class CampaignResponse(
    val id: Long,
    val workspaceId: Long,
    val name: String,
    val description: String?,
    val status: String,
    val objective: String,
    val totalBudget: Long,
    val currency: String,
    val fixedRewardPerCreator: Long,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val createdBy: Long,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val version: Long,
)

data class CampaignListResponse(
    val items: List<CampaignResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)

data class PlaybookStepResponse(
    val sortOrder: Int,
    val stepType: String,
    val title: String,
    val instruction: String?,
    val exampleUrl: String?,
    val required: Boolean,
)

data class PlaybookResponse(
    val id: Long,
    val campaignId: Long,
    val title: String,
    val summary: String?,
    val contentType: String,
    val revision: Int,
    val steps: List<PlaybookStepResponse>,
)

data class CampaignDetailResponse(
    val campaign: CampaignResponse,
    val playbook: PlaybookResponse?,
)

data class CreateCampaignRequest(
    val name: String,
    val description: String? = null,
    val objective: String = "AWARENESS",
    val totalBudget: Long = 0,
    val currency: String = "KRW",
    val fixedRewardPerCreator: Long = 0,
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null,
)

/** null 필드는 "변경 없음"으로 처리한다(MVP). */
data class UpdateCampaignRequest(
    val name: String? = null,
    val description: String? = null,
    val objective: String? = null,
    val totalBudget: Long? = null,
    val currency: String? = null,
    val fixedRewardPerCreator: Long? = null,
    val startAt: LocalDateTime? = null,
    val endAt: LocalDateTime? = null,
)

data class PlaybookStepRequest(
    val stepType: String = "INSTRUCTION",
    val title: String,
    val instruction: String? = null,
    val exampleUrl: String? = null,
    val required: Boolean = true,
)

data class UpsertPlaybookRequest(
    val title: String,
    val summary: String? = null,
    val contentType: String = "UGC_VIDEO",
    val steps: List<PlaybookStepRequest> = emptyList(),
)
