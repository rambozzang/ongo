package com.ongo.domain.creatormarketplace

import java.math.BigDecimal
import java.time.LocalDateTime

data class MarketplaceListing(
    val id: Long? = null,
    val userId: Long,
    val creatorName: String,
    val serviceType: String,
    val title: String,
    val description: String? = null,
    val price: Long = 0,
    val currency: String = "KRW",
    val rating: BigDecimal = BigDecimal.ZERO,
    val reviewCount: Int = 0,
    val deliveryDays: Int = 1,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime? = null,
)
