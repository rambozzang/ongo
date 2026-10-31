package com.ongo.application.ai.dto

import com.ongo.application.ai.estimate.AiEstimate

/**
 * 콘텐츠 갭 분석. 수요·경쟁·포화도는 모두 **AI 추정**이며 [aiEstimate] 가 무엇을 보고 추정했는지 말한다.
 * 화면은 "AI 추정" 배지와 근거를 함께 보여야 한다.
 */
data class ContentGapResponse(
    val opportunities: List<ContentOpportunity>,
    val oversaturated: List<OversaturatedTopic>,
    val analyzedAt: String,
    val aiEstimate: AiEstimate,
    /** 분석에 실제로 넣은 내 영상 수(실측). */
    val analyzedVideoCount: Int,
    val creditsUsed: Int,
)

/** [estimatedDemand]·[competitionLevel]: HIGH/MEDIUM/LOW, AI 가 판단하지 못했으면 null. */
data class ContentOpportunity(
    val topic: String,
    val estimatedDemand: String?,
    val competitionLevel: String?,
    val suggestedAngle: String,
)

data class OversaturatedTopic(
    val topic: String,
    val saturationLevel: String,
    val recommendation: String,
)
