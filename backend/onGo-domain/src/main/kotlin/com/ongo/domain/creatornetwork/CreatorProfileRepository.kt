package com.ongo.domain.creatornetwork

interface CreatorProfileRepository {
    fun findByUserId(userId: Long): List<CreatorProfile>
    fun findById(id: Long): CreatorProfile?
    fun findByCategory(category: String): List<CreatorProfile>
    fun save(profile: CreatorProfile): CreatorProfile
}
