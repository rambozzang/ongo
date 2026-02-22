package com.ongo.application.branddeal.dto

data class BrandDealResponse(
    val id: Long,
    val brandName: String,
    val contactName: String?,
    val contactEmail: String?,
    val dealValue: Long?,
    val currency: String,
    val status: String,
    val deadline: String?,
    val deliverables: List<String>,
    val notes: String?,
    val createdAt: String?,
)

data class CreateBrandDealRequest(
    val brandName: String,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val dealValue: Long? = null,
    val status: String = "INQUIRY",
    val deadline: String? = null,
    val notes: String? = null,
)

data class UpdateBrandDealRequest(
    val brandName: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val dealValue: Long? = null,
    val status: String? = null,
    val deadline: String? = null,
    val deliverables: List<String>? = null,
    val notes: String? = null,
)

data class MediaKitResponse(
    val id: Long,
    val displayName: String,
    val bio: String?,
    val categories: List<String>,
    val socialLinks: Map<String, String>,
    val rateCard: Map<String, Any>,
    val isPublic: Boolean,
    val slug: String?,
    val createdAt: String?,
)

data class UpdateMediaKitRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val categories: List<String>? = null,
    val socialLinks: Map<String, String>? = null,
    val rateCard: Map<String, Any>? = null,
    val isPublic: Boolean? = null,
    val slug: String? = null,
)
