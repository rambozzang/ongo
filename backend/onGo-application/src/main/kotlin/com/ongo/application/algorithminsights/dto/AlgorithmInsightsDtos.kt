package com.ongo.application.algorithminsights.dto

data class AlgorithmInsightResponse(
    val id: Long,
    val platform: String,
    val factor: String,
    val importance: Int,
    val currentScore: Int,
    val recommendation: String?,
    val category: String,
    val trend: String,
    val updatedAt: String,
)

data class AlgorithmScoreResponse(
    val platform: String,
    val overallScore: Int,
    val contentScore: Int,
    val engagementScore: Int,
    val metadataScore: Int,
    val consistencyScore: Int,
    val audienceScore: Int,
    val updatedAt: String,
)

data class AlgorithmChangeResponse(
    val id: Long,
    val platform: String,
    val title: String,
    val description: String,
    val impact: String,
    val affectedAreas: List<String>,
    val detectedAt: String,
    val recommendation: String?,
)

data class AlgorithmInsightsSummaryResponse(
    val avgOverallScore: Double,
    val bestPlatform: String,
    val worstFactor: String,
    val recentChanges: Int,
    val improvementSuggestions: Int,
)
