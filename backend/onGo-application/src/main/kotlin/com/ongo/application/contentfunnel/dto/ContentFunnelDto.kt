package com.ongo.application.contentfunnel.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class FunnelStageResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val stage: String,
    val count: Long,
    val rate: BigDecimal,
    val dropOff: BigDecimal,
    val measuredAt: LocalDateTime?,
)

data class FunnelComparisonResponse(
    val id: Long,
    val videoIdA: Long,
    val videoTitleA: String,
    val videoIdB: Long,
    val videoTitleB: String,
    val stagesA: List<FunnelStageResponse>,
    val stagesB: List<FunnelStageResponse>,
    val winner: String?,
    val createdAt: LocalDateTime?,
)

data class ContentFunnelSummaryResponse(
    val totalVideosAnalyzed: Int,
    val avgConversionRate: BigDecimal,
    val bestConvertingVideo: String,
    val worstDropOffStage: String,
    val avgViewToSubscribe: BigDecimal,
)
