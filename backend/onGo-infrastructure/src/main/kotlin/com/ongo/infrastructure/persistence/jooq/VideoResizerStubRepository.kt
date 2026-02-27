package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.videoresizer.*
import org.springframework.stereotype.Repository

@Repository
class VideoResizerStubRepository : VideoResizerRepository {
    override fun findById(id: Long): ResizeJob? = null
    override fun findByUserId(userId: Long): List<ResizeJob> = emptyList()
    override fun save(resizeJob: ResizeJob): ResizeJob = resizeJob.copy(id = 1)
    override fun update(resizeJob: ResizeJob): ResizeJob = resizeJob
    override fun delete(id: Long) {}
}
