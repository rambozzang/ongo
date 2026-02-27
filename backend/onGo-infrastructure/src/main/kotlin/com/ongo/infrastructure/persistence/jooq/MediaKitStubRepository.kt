package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.mediakit.*
import org.springframework.stereotype.Repository

@Repository
class MediaKitStubRepository : MediaKitRepository {
    override fun findById(id: Long): MediaKit? = null
    override fun findByUserId(userId: Long): List<MediaKit> = emptyList()
    override fun save(kit: MediaKit): MediaKit = kit.copy(id = 1)
    override fun update(kit: MediaKit): MediaKit = kit
    override fun delete(id: Long) {}
}
