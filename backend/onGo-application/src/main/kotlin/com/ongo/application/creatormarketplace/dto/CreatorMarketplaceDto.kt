package com.ongo.application.creatormarketplace.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class MarketplaceListingResponse(
    val id: Long,
    val creatorName: String,
    val serviceType: String,
    val title: String,
    val description: String?,
    val price: Long,
    val currency: String,
    val rating: BigDecimal,
    val reviewCount: Int,
    val deliveryDays: Int,
    val isActive: Boolean,
    val createdAt: LocalDateTime?,
)

data class MarketplaceOrderResponse(
    val id: Long,
    val listingId: Long,
    val buyerName: String,
    val sellerName: String,
    val status: String,
    val totalPrice: Long,
    val orderDate: LocalDateTime?,
    val deliveryDate: LocalDateTime?,
)

data class MarketplaceSummaryResponse(
    val totalListings: Int,
    val activeOrders: Int,
    val totalRevenue: Long,
    val avgRating: Double,
    val topServiceType: String,
)

data class CreateListingRequest(
    val creatorName: String,
    val serviceType: String,
    val title: String,
    val description: String? = null,
    val price: Long,
    val deliveryDays: Int = 1,
)
