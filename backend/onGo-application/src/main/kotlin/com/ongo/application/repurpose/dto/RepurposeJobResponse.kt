package com.ongo.application.repurpose.dto

data class RepurposeJobResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val status: String,
    val clipCount: Int,
    val createdAt: String,
)

data class RepurposeClipResponse(
    val id: Long,
    val jobId: Long,
    val startTime: String,
    val endTime: String,
    val title: String,
    val description: String,
    val viralScore: Int,
    val reasoning: String,
    val suggestedPlatform: String,
)

data class RepurposeDetailResponse(
    val job: RepurposeJobResponse,
    val clips: List<RepurposeClipResponse>,
)
