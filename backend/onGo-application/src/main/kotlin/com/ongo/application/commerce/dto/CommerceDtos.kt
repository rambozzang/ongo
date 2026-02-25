package com.ongo.application.commerce.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class CommercePlatformResponse(
    val id: Long,
    val platformType: String,
    val platformName: String?,
    val status: String,
    val createdAt: LocalDateTime?,
)

data class ConnectPlatformRequest(
    val platformType: String,
    val platformName: String? = null,
    val accessToken: String? = null,
)

data class CommerceProductResponse(
    val id: Long,
    val commercePlatformId: Long?,
    val productName: String?,
    val productUrl: String?,
    val imageUrl: String?,
    val price: BigDecimal?,
    val affiliateLink: String?,
    val createdAt: LocalDateTime?,
)

data class CreateProductRequest(
    val commercePlatformId: Long? = null,
    val productName: String? = null,
    val productUrl: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
    val affiliateLink: String? = null,
)

data class UpdateProductRequest(
    val productName: String? = null,
    val productUrl: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
    val affiliateLink: String? = null,
)

data class ProductVideoLinkResponse(
    val id: Long,
    val productId: Long,
    val videoId: Long,
    val clicks: Int,
    val conversions: Int,
    val revenue: BigDecimal,
    val createdAt: LocalDateTime?,
)

data class LinkProductVideoRequest(
    val productId: Long,
    val videoId: Long,
)
