package com.ongo.application.channelaudit

import java.time.LocalDateTime

data class ChannelAuditResponse(
    val id: Long,
    val overallScore: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val actionItems: List<ActionItemResponse>,
    val outlierVideos: List<OutlierVideoResponse>,
    val growthForecast: String,
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
