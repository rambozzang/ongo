package com.ongo.domain.subtitlegenerator

interface SubtitleSegmentRepository {
    fun findByJobId(jobId: Long): List<SubtitleSegment>
    fun saveAll(segments: List<SubtitleSegment>): List<SubtitleSegment>
}
