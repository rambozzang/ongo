package com.ongo.domain.audiencesegment

interface AudienceSegmentRepository {
    fun findById(id: Long): AudienceSegment?
    fun findByUserId(userId: Long): List<AudienceSegment>
    fun save(segment: AudienceSegment): AudienceSegment
    fun update(segment: AudienceSegment): AudienceSegment
    fun delete(id: Long)
}
