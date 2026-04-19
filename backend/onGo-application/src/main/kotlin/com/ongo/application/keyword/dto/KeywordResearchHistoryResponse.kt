package com.ongo.application.keyword.dto

data class KeywordResearchHistoryResponse(
    val items: List<KeywordResearchSummary>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

data class KeywordResearchSummary(
    val id: Long,
    val keyword: String,
    val platforms: List<String>,
    val createdAt: String,
)
