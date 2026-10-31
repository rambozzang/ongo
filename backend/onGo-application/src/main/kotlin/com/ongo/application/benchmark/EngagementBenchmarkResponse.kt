package com.ongo.application.benchmark

import com.ongo.application.ai.estimate.AiEstimate

/**
 * 참여율 진단. 참여율 숫자는 **전부 실측**(최근 30일, (좋아요+댓글)/조회수, 수집하는 지표만)이고,
 * 측정할 수 없으면 null 이다. 강점·개선점은 AI 가 그 실측값만 보고 쓴 문장이라 [aiEstimate] 로 표시한다.
 *
 * 업계 평균·백분위는 없다 — 비교할 공개 데이터를 갖고 있지 않다.
 */
data class EngagementBenchmarkResponse(
    val periodDays: Int,
    val overallEngagementRate: Double?,
    val platforms: List<PlatformEngagement>,
    val strengths: List<String>,
    val improvements: List<String>,
    val aiEstimate: AiEstimate,
    val creditsUsed: Int,
) {
    data class PlatformEngagement(val platform: String, val engagementRate: Double?)
}
