package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.brandvoice.*
import org.springframework.stereotype.Repository

@Repository
class BrandVoiceStubRepository : BrandVoiceRepository {
    override fun findById(id: Long): BrandVoiceProfile? = null
    override fun findByUserId(userId: Long): List<BrandVoiceProfile> = emptyList()
    override fun save(profile: BrandVoiceProfile): BrandVoiceProfile = profile.copy(id = 1)
    override fun update(profile: BrandVoiceProfile): BrandVoiceProfile = profile
    override fun delete(id: Long) {}
}
