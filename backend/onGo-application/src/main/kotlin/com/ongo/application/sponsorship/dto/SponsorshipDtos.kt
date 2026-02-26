package com.ongo.application.sponsorship.dto

data class SponsorshipResponse(
    val id: Long,
    val brandName: String,
    val brandLogo: String?,
    val contactName: String,
    val contactEmail: String,
    val status: String,
    val dealValue: Long,
    val currency: String,
    val startDate: String,
    val endDate: String,
    val deliverables: List<DeliverableResponse>,
    val notes: String?,
    val contractUrl: String?,
    val paymentStatus: String,
    val paidAmount: Long,
    val createdAt: String,
    val updatedAt: String,
)

data class DeliverableResponse(
    val id: Long,
    val sponsorshipId: Long,
    val type: String,
    val title: String,
    val description: String?,
    val dueDate: String,
    val isCompleted: Boolean,
    val videoId: String?,
    val platform: String,
    val completedAt: String?,
)

data class CreateSponsorshipRequest(
    val brandName: String,
    val contactName: String,
    val contactEmail: String,
    val dealValue: Long,
    val currency: String? = "KRW",
    val startDate: String,
    val endDate: String,
    val notes: String? = null,
)

data class UpdateSponsorshipRequest(
    val status: String? = null,
    val dealValue: Long? = null,
    val paymentStatus: String? = null,
    val paidAmount: Long? = null,
    val notes: String? = null,
)

data class CreateDeliverableRequest(
    val sponsorshipId: Long,
    val type: String,
    val title: String,
    val description: String? = null,
    val dueDate: String,
    val platform: String,
)

data class SponsorshipSummaryResponse(
    val totalDeals: Int,
    val activeDeals: Int,
    val totalRevenue: Long,
    val pendingPayments: Long,
    val completionRate: Double,
    val avgDealValue: Long,
)
