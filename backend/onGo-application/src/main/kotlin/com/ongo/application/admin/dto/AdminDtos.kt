package com.ongo.application.admin.dto

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus

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
    val subscriberCount: Long,
    val status: String,
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
    val storageQuotaOverride: Long?,
    val cancelledAt: String?,
    val createdAt: String?,
)

data class UpdateRoleRequest(
    val role: String,
)
