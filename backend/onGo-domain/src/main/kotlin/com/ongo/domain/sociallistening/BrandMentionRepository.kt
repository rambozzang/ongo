package com.ongo.domain.sociallistening

interface BrandMentionRepository {
    fun findById(id: Long): BrandMention?
    fun findByUserId(userId: Long): List<BrandMention>
    fun findByUserIdAndPeriod(userId: Long, days: Int): List<BrandMention>
    fun save(mention: BrandMention): BrandMention
    fun update(mention: BrandMention): BrandMention
    fun delete(id: Long)
}
