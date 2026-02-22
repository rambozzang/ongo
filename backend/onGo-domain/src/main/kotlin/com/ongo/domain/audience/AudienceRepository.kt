package com.ongo.domain.audience

interface AudienceRepository {
    fun findProfilesByUserId(userId: Long, sortBy: String = "engagement_score", limit: Int = 50, offset: Int = 0): List<AudienceProfile>
    fun findProfileById(id: Long): AudienceProfile?
    fun countProfilesByUserId(userId: Long): Long
    fun saveProfile(profile: AudienceProfile): AudienceProfile
    fun upsertProfile(profile: AudienceProfile): AudienceProfile
    fun updateProfileTags(id: Long, tags: String)

    fun findSegmentsByUserId(userId: Long): List<AudienceSegment>
    fun findSegmentById(id: Long): AudienceSegment?
    fun saveSegment(segment: AudienceSegment): AudienceSegment
    fun updateSegment(id: Long, name: String?, description: String?, conditions: String?)
    fun deleteSegment(id: Long)
}
