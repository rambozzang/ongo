package com.ongo.domain.creatormarketplace

interface MarketplaceListingRepository {
    fun findById(id: Long): MarketplaceListing?
    fun findByUserId(userId: Long): List<MarketplaceListing>
    fun findByServiceType(serviceType: String): List<MarketplaceListing>
    fun save(listing: MarketplaceListing): MarketplaceListing
    fun deleteById(id: Long)
}
