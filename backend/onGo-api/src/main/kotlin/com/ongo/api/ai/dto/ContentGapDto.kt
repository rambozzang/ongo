package com.ongo.api.ai.dto

data class ContentGapRequest(
    val channelIds: List<Long>? = null,
    val includeCompetitors: Boolean = true,
)

data class ContentGapResponse(
    val opportunities: List<ContentOpportunity>,
    val oversaturated: List<OversaturatedTopic>,
    val analyzedAt: String,
)

data class ContentOpportunity(
    val topic: String,
    val estimatedDemand: String,
    val competitionLevel: String,
    val suggestedAngle: String,
    val relevanceScore: Double,
)

data class OversaturatedTopic(
    val topic: String,
    val saturationLevel: String,
    val recommendation: String,
)
