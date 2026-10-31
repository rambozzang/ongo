package com.ongo.application.keyword.dto

import com.ongo.application.ai.estimate.AiEstimate

/**
 * 키워드 리서치. 검색량·경쟁도·추세는 전부 **AI 추정**이며 [aiEstimate] 가 근거를 말한다.
 * 실제 검색량 데이터를 쓰지 않으므로 숫자 점수는 내지 않는다.
 */
data class KeywordResearchResponse(
    val id: Long,
    val keyword: String,
    val platforms: List<PlatformKeywordAnalysisDto>,
    val overallOpportunity: String,
    val suggestions: List<String>,
    val aiEstimate: AiEstimate,
    val creditsUsed: Int,
    val createdAt: String,
)

/** 각 값은 정해진 등급 중 하나이고, AI 가 판단하지 못했으면 null. */
data class PlatformKeywordAnalysisDto(
    val platform: String,
    /** HIGH / MEDIUM / LOW */
    val searchVolume: String?,
    /** HIGH / MEDIUM / LOW */
    val competition: String?,
    /** RISING / STABLE / DECLINING */
    val trend: String?,
    val relatedKeywords: List<String>,
)
