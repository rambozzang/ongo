package com.ongo.domain.brandkit

interface BrandKitRepository {
    fun findById(id: Long): BrandKit?
    fun findByUserId(userId: Long): List<BrandKit>
    fun save(brandKit: BrandKit): BrandKit
    fun update(brandKit: BrandKit): BrandKit
    fun delete(id: Long)
    fun clearDefault(userId: Long)
}
