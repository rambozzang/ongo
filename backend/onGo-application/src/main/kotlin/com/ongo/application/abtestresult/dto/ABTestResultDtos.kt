package com.ongo.application.abtestresult.dto

data class ABTestResultResponse(
    val id: Long,
    val testId: Long?,
    val testName: String,
    val status: String,
    val startDate: String,
    val endDate: String?,
    val variants: List<TestVariantResponse>,
    val winner: String?,
    val confidence: Double,
    val metric: String,
    val platform: String,
    val createdAt: String,
)

data class TestVariantResponse(
    val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val views: Long,
    val clicks: Long,
    val ctr: Double,
    val avgWatchTime: Int,
    val engagement: Double,
    val conversions: Int,
    val isControl: Boolean,
    val isWinner: Boolean,
)

data class ABTestResultSummaryResponse(
    val totalTests: Int,
    val runningTests: Int,
    val completedTests: Int,
    val avgConfidence: Double,
    val avgImprovement: Double,
)
