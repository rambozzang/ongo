package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatormarketplace.*
import org.springframework.stereotype.Repository

@Repository
class MarketplaceListingStubRepository : MarketplaceListingRepository {
    override fun findById(id: Long): MarketplaceListing? = null
    override fun findByUserId(userId: Long): List<MarketplaceListing> = emptyList()
    override fun findByServiceType(serviceType: String): List<MarketplaceListing> = emptyList()
    override fun save(listing: MarketplaceListing): MarketplaceListing = listing.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class MarketplaceOrderStubRepository : MarketplaceOrderRepository {
    override fun findByBuyerId(buyerId: Long): List<MarketplaceOrder> = emptyList()
    override fun findBySellerId(sellerId: Long): List<MarketplaceOrder> = emptyList()
    override fun save(order: MarketplaceOrder): MarketplaceOrder = order.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}
