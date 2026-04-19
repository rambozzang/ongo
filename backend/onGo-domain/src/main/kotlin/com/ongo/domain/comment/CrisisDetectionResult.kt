package com.ongo.domain.comment

data class CrisisDetectionResult(
    val isInCrisis: Boolean,
    val currentNegativeCount: Int,
    val previousNegativeCount: Int,
    val changePercent: Double,
    val topKeywords: List<String>,
)
