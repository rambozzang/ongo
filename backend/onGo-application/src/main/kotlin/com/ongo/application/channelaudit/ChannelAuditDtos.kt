package com.ongo.application.channelaudit

import com.ongo.application.ai.estimate.AiEstimate

import java.time.LocalDateTime

/**
 * 채널 감사 보고서.
 *
 * - [measured]: 보고서를 만든 시점의 **실측** 30일 지표. 이 기능 도입 전 보고서는 null.
 * - [overallScore]: **예전 보고서에만 있는 AI 점수**. 새 보고서는 null 이다. 값이 있으면 [overallScoreEstimate] 도
 *   함께 오며, 화면은 "AI 추정" 배지 없이 이 숫자를 보이면 안 된다.
 * - [growthForecast]: AI 가 쓴 전망 문장. [forecastEstimate] 로 표시한다.
 */
data class ChannelAuditResponse(
    val id: Long,
    val overallScore: Int?,
    val overallScoreEstimate: AiEstimate?,
    val measured: ChannelAuditMeasured?,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<ActionItemResponse>,
    val outlierVideos: List<OutlierVideoResponse>,
    val growthForecast: String,
    val forecastEstimate: AiEstimate,
    val createdAt: LocalDateTime,
) {
    data class ActionItemResponse(
        val priority: String,
        val action: String,
        val expectedImpact: String,
    )

    data class OutlierVideoResponse(
        val videoTitle: String,
        val metric: String,
        val reason: String,
    )
}

data class ChannelAuditListResponse(
    val audits: List<ChannelAuditResponse>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
)

/**
 * 감사 시점의 실측 지표(최근 [periodDays]일). 수집하지 않는 지표는 null — 0 으로 채우지 않는다.
 */
data class ChannelAuditMeasured(
    val periodDays: Int,
    val totalViews: Long?,
    /** 이전 같은 기간 대비 조회수 증감(%). 기준이 없으면 null. */
    val totalViewsChange: Double?,
    val totalLikes: Long?,
    val totalComments: Long?,
    /** 구독자 수를 실제로 조회하는 채널만 더한 합계. */
    val subscriberCount: Long?,
    /** 분석에 넣은 상위 영상 수. */
    val analyzedVideoCount: Int,
)
