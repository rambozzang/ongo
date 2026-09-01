package com.ongo.application.admin.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.channel.ChannelStatus

data class AdminUserListItem(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val planType: String,
    val storageUsedBytes: Long,
    val storageLimitBytes: Long,
    val createdAt: String?,
)

data class AdminUserDetail(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val planType: String,
    val storageUsedBytes: Long,
    val storageLimitBytes: Long,
    val storageQuotaOverride: Long?,
    val videoCount: Long,
    val createdAt: String?,
)

// --- 1단계: CS 대응용 Admin DTOs ---

data class AdminVideoItem(
    val id: Long,
    val title: String,
    val status: UploadStatus,
    val mediaType: String,
    val fileSizeBytes: Long?,
    val platforms: List<AdminPlatformUploadItem>,
    val createdAt: String?,
)

data class AdminPlatformUploadItem(
    val platform: Platform,
    val status: UploadStatus,
    val platformUrl: String?,
    val errorMessage: String?,
)

data class AdminChannelItem(
    val id: Long,
    val platform: Platform,
    val channelName: String,
    val channelUrl: String?,
    /**
     * 구독자(팔로워) 수. **그 플랫폼이 조회하지 않으면 `null`** —
     * [com.ongo.application.channel.dto.ChannelResponse.subscriberCount] 와 같은 계약이다.
     *
     * 운영자가 사용자 채널을 확인하는 화면이라, 재지 않은 값을 "구독자 0명" 으로 보이면
     * 실제 문제가 있는 채널과 구분되지 않는다.
     */
    val subscriberCount: Long?,
    val status: ChannelStatus,
    val tokenExpiresAt: String?,
    val connectedAt: String?,
)

data class AdminSubscriptionDetail(
    val planType: String,
    val status: String,
    val price: Int,
    val billingCycle: String,
    val currentPeriodStart: String?,
    val currentPeriodEnd: String?,
    val nextBillingDate: String?,
    val pendingPlanType: String?,
    val pendingBillingCycle: String?,
    val storageQuotaOverride: Long?,
    val cancelledAt: String?,
    val createdAt: String?,
)

/** 게시 worker의 현재 대기열을 관리자에게 보여주는 운영 상태 DTO. */
data class AdminPublishQueueSummary(
    val capturedAt: String,
    val totalPending: Int,
    val statusCounts: Map<String, Int>,
    val activeLeases: Int,
    val dueRetries: Int,
    val unconfirmed: Int,
    val items: List<AdminPublishQueueItem>,
)

data class AdminPublishQueueItem(
    val uploadId: Long,
    val videoId: Long,
    val platform: Platform,
    val status: UploadStatus,
    val attemptCount: Int,
    val nextRetryAt: String?,
    val leaseUntil: String?,
    val lastError: String?,
    val errorMessage: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

data class UpdateRoleRequest(
    val role: String,
)
