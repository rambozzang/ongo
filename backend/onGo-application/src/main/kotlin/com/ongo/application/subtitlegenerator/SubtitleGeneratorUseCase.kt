package com.ongo.application.subtitlegenerator

import com.ongo.application.subtitlegenerator.dto.*
import com.ongo.domain.subtitlegenerator.SubtitleJobRepository
import com.ongo.domain.subtitlegenerator.SubtitleSegmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class SubtitleGeneratorUseCase(
    private val jobRepo: SubtitleJobRepository,
    private val segmentRepo: SubtitleSegmentRepository,
) {
    fun getJobs(userId: Long, status: String?): List<SubtitleJobResponse> {
        val jobs = if (status != null) jobRepo.findByUserIdAndStatus(userId, status)
        else jobRepo.findByUserId(userId)
        return jobs.map { SubtitleJobResponse.from(it) }
    }

    fun getJob(id: Long): SubtitleJobResponse? =
        jobRepo.findById(id)?.let { SubtitleJobResponse.from(it) }

    fun getSegments(jobId: Long): List<SubtitleSegmentResponse> =
        segmentRepo.findByJobId(jobId).map { SubtitleSegmentResponse.from(it) }

    fun getSummary(userId: Long): SubtitleGeneratorSummaryResponse {
        val jobs = jobRepo.findByUserId(userId)
        val completed = jobs.filter { it.status == "COMPLETED" }
        return SubtitleGeneratorSummaryResponse(
            totalJobs = jobs.size,
            completedJobs = completed.size,
            avgAccuracy = 93.5,
            totalWords = completed.sumOf { it.wordCount.toLong() },
            recentLanguage = jobs.maxByOrNull { it.createdAt }?.language ?: "ko",
        )
    }
}
