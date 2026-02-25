package com.ongo.domain.commerce

import java.math.BigDecimal
import java.time.LocalDateTime

data class CommercePlatform(
    val id: Long? = null,
    val userId: Long,
    val platformType: String,
    val platformName: String? = null,
    val accessTokenEnc: String? = null,
    val status: String = "CONNECTED",
    val createdAt: LocalDateTime? = null,
)

data class CommerceProduct(
    val id: Long? = null,
    val userId: Long,
    val commercePlatformId: Long? = null,
    val productName: String? = null,
    val productUrl: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
    val affiliateLink: String? = null,
    val createdAt: LocalDateTime? = null,
)

data class ProductVideoLink(
    val id: Long? = null,
    val productId: Long,
    val videoId: Long,
    val clicks: Int = 0,
    val conversions: Int = 0,
    val revenue: BigDecimal = BigDecimal.ZERO,
    val createdAt: LocalDateTime? = null,
)
