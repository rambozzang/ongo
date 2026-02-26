package com.ongo.domain.creatormarketplace

import java.time.LocalDateTime

data class MarketplaceOrder(
    val id: Long? = null,
    val listingId: Long,
    val buyerId: Long,
    val buyerName: String,
    val sellerId: Long,
    val sellerName: String,
    val status: String = "PENDING",
    val totalPrice: Long = 0,
    val orderDate: LocalDateTime? = null,
    val deliveryDate: LocalDateTime? = null,
)
