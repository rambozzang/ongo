package com.ongo.domain.creatormarketplace

interface MarketplaceOrderRepository {
    fun findByBuyerId(buyerId: Long): List<MarketplaceOrder>
    fun findBySellerId(sellerId: Long): List<MarketplaceOrder>
    fun save(order: MarketplaceOrder): MarketplaceOrder
    fun updateStatus(id: Long, status: String)
}
