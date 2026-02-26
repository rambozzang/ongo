package com.ongo.application.revenuesplit.dto

data class RevenueSplitResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val totalAmount: Long,
    val currency: String,
    val status: String,
    val period: String?,
    val members: List<SplitMemberResponse>,
    val createdAt: String,
    val updatedAt: String,
)

data class SplitMemberResponse(
    val id: Long,
    val name: String,
    val email: String?,
    val role: String?,
    val percentage: Double,
    val amount: Long,
    val paymentStatus: String,
    val paidAt: String?,
)

data class CreateRevenueSplitRequest(
    val title: String,
    val description: String? = null,
    val totalAmount: Long,
    val currency: String = "KRW",
    val period: String? = null,
    val members: List<CreateSplitMemberRequest>,
)

data class CreateSplitMemberRequest(
    val name: String,
    val email: String? = null,
    val role: String? = null,
    val percentage: Double,
)

data class RevenueSplitSummaryResponse(
    val totalSplits: Int,
    val pendingAmount: Long,
    val distributedAmount: Long,
    val totalMembers: Int,
    val averageSplit: Long,
)
