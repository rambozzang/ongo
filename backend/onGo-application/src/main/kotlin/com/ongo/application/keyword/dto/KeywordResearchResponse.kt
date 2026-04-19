package com.ongo.application.keyword.dto

data class KeywordResearchResponse(
    val id: Long,
    val keyword: String,
    val platforms: List<PlatformKeywordAnalysisDto>,
    val overallOpportunity: String,
    val suggestions: List<String>,
    val createdAt: String,
)

data class PlatformKeywordAnalysisDto(
    val platform: String,
    val searchVolume: String,
    val competition: String,
    val trend: String,
    val opportunityScore: Int,
    val relatedKeywords: List<String>,
)
