package com.ongo.application.subtitlegenerator.dto

import com.ongo.domain.subtitlegenerator.SubtitleJob
import com.ongo.domain.subtitlegenerator.SubtitleSegment
import java.math.BigDecimal

data class SubtitleJobResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val platform: String,
    val language: String,
    val status: String,
    val progress: Int,
    val subtitleUrl: String?,
    val wordCount: Int,
    val duration: Int,
    val createdAt: String,
    val completedAt: String?,
) {
    companion object {
        fun from(e: SubtitleJob) = SubtitleJobResponse(
            id = e.id, videoId = e.videoId, videoTitle = e.videoTitle,
            platform = e.platform, language = e.language, status = e.status,
            progress = e.progress, subtitleUrl = e.subtitleUrl,
            wordCount = e.wordCount, duration = e.duration,
            createdAt = e.createdAt.toString(), completedAt = e.completedAt?.toString(),
        )
    }
}

data class SubtitleSegmentResponse(
    val id: Long,
    val jobId: Long,
    val startTime: BigDecimal,
    val endTime: BigDecimal,
    val text: String,
    val confidence: BigDecimal,
) {
    companion object {
        fun from(e: SubtitleSegment) = SubtitleSegmentResponse(
            id = e.id, jobId = e.jobId, startTime = e.startTime,
            endTime = e.endTime, text = e.text, confidence = e.confidence,
        )
    }
}

data class SubtitleGeneratorSummaryResponse(
    val totalJobs: Int,
    val completedJobs: Int,
    val avgAccuracy: Double,
    val totalWords: Long,
    val recentLanguage: String,
)

data class GenerateSubtitleRequest(
    val videoId: Long,
    val language: String = "ko",
)
