package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.subtitlegenerator.*
import org.springframework.stereotype.Repository

@Repository
class SubtitleJobStubRepository : SubtitleJobRepository {
    override fun findByUserId(userId: Long): List<SubtitleJob> = emptyList()
    override fun findById(id: Long): SubtitleJob? = null
    override fun findByUserIdAndStatus(userId: Long, status: String): List<SubtitleJob> = emptyList()
    override fun save(job: SubtitleJob): SubtitleJob = job.copy(id = 1)
    override fun updateStatus(id: Long, status: String, progress: Int) {}
}

@Repository
class SubtitleSegmentStubRepository : SubtitleSegmentRepository {
    override fun findByJobId(jobId: Long): List<SubtitleSegment> = emptyList()
    override fun saveAll(segments: List<SubtitleSegment>): List<SubtitleSegment> = segments
}
