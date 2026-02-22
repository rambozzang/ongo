package com.ongo.api.ai.dto

data class StrategyCoachRequest(
    val includeCompetitors: Boolean = true,
    val focusArea: String? = null,
)

data class StrategyCoachResponse(
    val contentRecommendations: List<ContentRecommendation>,
    val platformStrategy: List<PlatformStrategyItem>,
    val timingAdvice: List<TimingAdvice>,
    val overallStrategy: String,
) {
    data class ContentRecommendation(
        val topic: String,
        val targetPlatform: String,
        val reason: String,
        val priority: String,
        val expectedImpact: String,
    )

    data class PlatformStrategyItem(
        val platform: String,
        val strength: String,
        val opportunity: String,
        val action: String,
    )

    data class TimingAdvice(
        val recommendation: String,
        val reason: String,
        val expectedBoost: String,
    )
}
