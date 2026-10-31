package com.ongo.application.videoseo.dto

import com.ongo.application.ai.estimate.AiEstimate

/**
 * 영상 SEO 점검 결과. 프런트 `SeoScoreResponse` 와 필드명이 같아야 한다.
 *
 * 점수는 **규칙 점수**(실측)다 — [scoreBasis] 가 그 사실을 말하고, [categories] 의 항목마다 통과 여부와
 * 실제 값이 있다. AI 가 만든 것은 [suggestions] 와 [relatedKeywords] 뿐이고, 둘 다 [aiEstimate] 로 표시한다.
 */
data class VideoSeoScoreResponse(
    val videoId: Long,
    val overallScore: Int,
    val scoreBasis: String,
    val categories: List<SeoCategoryResponse>,
    val suggestions: List<String>,
    val relatedKeywords: List<String>,
    val aiEstimate: AiEstimate,
    /** 실제로 차감한 크레딧. */
    val creditsUsed: Int,
)

data class SeoCategoryResponse(
    val key: String,
    val name: String,
    val score: Int,
    val maxScore: Int,
    val checks: List<SeoCheckResponse>,
)

data class SeoCheckResponse(
    val key: String,
    val label: String,
    val passed: Boolean,
    val points: Int,
    val maxPoints: Int,
    val detail: String,
)
