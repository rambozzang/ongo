package com.ongo.domain.brandvoice

interface BrandVoiceRepository {
    fun findById(id: Long): BrandVoiceProfile?
    fun findByUserId(userId: Long): List<BrandVoiceProfile>
    fun save(profile: BrandVoiceProfile): BrandVoiceProfile
    fun update(profile: BrandVoiceProfile): BrandVoiceProfile
    fun delete(id: Long)
}
