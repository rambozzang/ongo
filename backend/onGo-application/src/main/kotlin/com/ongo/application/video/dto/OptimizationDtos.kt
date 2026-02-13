package com.ongo.application.video.dto

import com.ongo.common.enums.Platform

enum class OptimizationSeverity {
    GOOD, WARNING, ERROR
}

data class OptimizationSuggestion(
    val field: String,
    val severity: OptimizationSeverity,
    val message: String,
    val currentValue: String? = null,
    val recommendedValue: String? = null,
)

data class OptimizationResult(
    val platform: Platform,
    val score: Int,
    val suggestions: List<OptimizationSuggestion>,
)

data class OptimizationCheckRequest(
    val title: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val platforms: List<Platform> = emptyList(),
)

data class OptimizationCheckResponse(
    val results: List<OptimizationResult>,
)
