package com.ongo.domain.repurpose

interface RepurposeClipRepository {
    fun findByJobId(jobId: Long): List<RepurposeClip>
    fun saveAll(clips: List<RepurposeClip>): List<RepurposeClip>
}
