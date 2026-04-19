package com.ongo.domain.repurpose

interface RepurposeJobRepository {
    fun findById(id: Long): RepurposeJob?
    fun findByUserId(userId: Long): List<RepurposeJob>
    fun save(job: RepurposeJob): RepurposeJob
    fun updateStatus(id: Long, status: String, clipCount: Int): RepurposeJob?
}
