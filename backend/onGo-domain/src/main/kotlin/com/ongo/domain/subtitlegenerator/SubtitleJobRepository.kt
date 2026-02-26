package com.ongo.domain.subtitlegenerator

interface SubtitleJobRepository {
    fun findByUserId(userId: Long): List<SubtitleJob>
    fun findById(id: Long): SubtitleJob?
    fun findByUserIdAndStatus(userId: Long, status: String): List<SubtitleJob>
    fun save(job: SubtitleJob): SubtitleJob
    fun updateStatus(id: Long, status: String, progress: Int)
}
