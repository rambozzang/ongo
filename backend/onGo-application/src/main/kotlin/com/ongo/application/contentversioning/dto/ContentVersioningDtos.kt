package com.ongo.application.contentversioning.dto

data class ContentVersionGroupResponse(
    val contentId: Long,
    val contentTitle: String,
    val platform: String,
    val totalVersions: Int,
    val latestVersion: Int,
    val versions: List<ContentVersionResponse>,
    val createdAt: String,
)

data class ContentVersionResponse(
    val id: Long,
    val versionNumber: Int,
    val changeType: String,
    val changeDescription: String,
    val previousValue: String?,
    val newValue: String?,
    val performanceBefore: VersionPerformanceResponse?,
    val performanceAfter: VersionPerformanceResponse?,
    val createdBy: String,
    val createdAt: String,
)

data class VersionPerformanceResponse(
    val views: Long,
    val likes: Int,
    val engagement: Double,
    val ctr: Double,
)

data class ContentVersioningSummaryResponse(
    val totalContents: Int,
    val totalVersions: Int,
    val avgVersionsPerContent: Double,
    val mostEditedContent: String,
    val bestPerformingChange: String,
)
