package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.audiencesegment.*
import org.springframework.stereotype.Repository

@Repository
class AudienceSegmentStubRepository : AudienceSegmentRepository {
    override fun findById(id: Long): AudienceSegment? = null
    override fun findByUserId(userId: Long): List<AudienceSegment> = emptyList()
    override fun save(segment: AudienceSegment): AudienceSegment = segment.copy(id = 1)
    override fun update(segment: AudienceSegment): AudienceSegment = segment
    override fun delete(id: Long) {}
}
