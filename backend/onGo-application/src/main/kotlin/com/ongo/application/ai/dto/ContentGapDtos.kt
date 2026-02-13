package com.ongo.application.ai.dto

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
