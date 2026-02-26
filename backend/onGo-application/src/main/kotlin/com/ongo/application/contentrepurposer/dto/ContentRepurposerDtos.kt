package com.ongo.application.contentrepurposer.dto

data class RepurposeJobResponse(
    val id: Long,
    val originalTitle: String,
    val originalPlatform: String,
    val targetPlatform: String,
    val targetFormat: String,
    val status: String,
    val progress: Int,
    val outputUrl: String?,
    val createdAt: String,
)

data class RepurposeTemplateResponse(
    val id: Long,
    val name: String,
    val sourcePlatform: String,
    val targetPlatform: String,
    val targetFormat: String,
    val description: String?,
    val isDefault: Boolean,
)

data class RepurposeRequest(
    val videoId: Long,
    val targetPlatform: String,
    val targetFormat: String,
    val templateId: Long? = null,
)

data class ContentRepurposerSummaryResponse(
    val totalJobs: Int,
    val completedJobs: Int,
    val avgTimeSaved: Double,
    val topTargetPlatform: String,
    val successRate: Double,
)
