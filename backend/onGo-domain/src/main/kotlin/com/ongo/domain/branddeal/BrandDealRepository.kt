package com.ongo.domain.branddeal

interface BrandDealRepository {
    fun findDealsByUserId(userId: Long, status: String? = null): List<BrandDeal>
    fun findDealById(id: Long): BrandDeal?
    fun saveDeal(deal: BrandDeal): BrandDeal
    fun updateDeal(id: Long, brandName: String?, contactName: String?, contactEmail: String?, dealValue: Long?, status: String?, deadline: java.time.LocalDate?, deliverables: String?, notes: String?)
    fun deleteDeal(id: Long)

    fun findMediaKitByUserId(userId: Long): MediaKit?
    fun findMediaKitBySlug(slug: String): MediaKit?
    fun saveMediaKit(kit: MediaKit): MediaKit
    fun updateMediaKit(id: Long, displayName: String?, bio: String?, categories: String?, socialLinks: String?, statsSnapshot: String?, rateCard: String?, isPublic: Boolean?, slug: String?)
}
