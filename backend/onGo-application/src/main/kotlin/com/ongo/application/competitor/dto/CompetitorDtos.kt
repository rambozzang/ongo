package com.ongo.application.competitor.dto

import java.time.LocalDateTime

data class CompetitorResponse(
    val id: Long,
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String?,
    val subscriberCount: Long,
    val totalViews: Long,
    val videoCount: Int,
    val avgViews: Long,
    val profileImageUrl: String?,
    val lastSyncedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CompetitorListResponse(
    val competitors: List<CompetitorResponse>,
    val totalCount: Int,
)

/** 경쟁 채널 한 건의 동기화 결과. status 는 SYNCED / UNSUPPORTED / FAILED. */
data class CompetitorSyncItemResponse(
    val competitorId: Long?,
    val channelName: String,
    val platform: String,
    val status: String,
    val message: String? = null,
)

/**
 * 수동 동기화 결과.
 *
 * 건수를 그대로 노출한다. 예전에는 저장된 목록을 그대로 돌려주면서 "동기화가
 * 완료되었습니다"라고만 답해서, 실제로 아무것도 갱신되지 않아도 성공으로 보였다.
 */
data class CompetitorSyncResponse(
    val requested: Int,
    val synced: Int,
    val unsupported: Int,
    val failed: Int,
    val results: List<CompetitorSyncItemResponse>,
    val competitors: List<CompetitorResponse>,
    val totalCount: Int,
)

data class CreateCompetitorRequest(
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val totalViews: Long = 0,
    val videoCount: Int = 0,
    val avgViews: Long = 0,
    val profileImageUrl: String? = null,
)

data class UpdateCompetitorRequest(
    val channelName: String? = null,
    val channelUrl: String? = null,
    val subscriberCount: Long? = null,
    val totalViews: Long? = null,
    val videoCount: Int? = null,
    val avgViews: Long? = null,
    val profileImageUrl: String? = null,
)

data class ChannelLookupRequest(
    val platform: String,
    val query: String,
)

data class ChannelLookupResponse(
    val found: Boolean,
    val platformChannelId: String? = null,
    val channelName: String? = null,
    val channelUrl: String? = null,
    val subscriberCount: Long = 0,
    val totalViews: Long = 0,
    val videoCount: Int = 0,
    val profileImageUrl: String? = null,
    val platform: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String? = null,
)

data class CompetitorTrendRequest(
    val competitorIds: List<Long> = emptyList(),
    val days: Int = 30,
)

data class CompetitorTrendPoint(
    val date: String,
    val subscriberCount: Long,
    val avgViews: Long,
    val totalViews: Long,
)

data class CompetitorTrendResponse(
    val competitorId: Long,
    val channelName: String,
    val data: List<CompetitorTrendPoint>,
)

data class BenchmarkResponse(
    val myStats: MyChannelStats,
    val competitors: List<CompetitorBenchmark>,
)

data class MyChannelStats(
    val subscriberCount: Long,
    val totalViews: Long,
    val videoCount: Int,
    val avgViews: Long,
    val engagementRate: Double,
    val growthRate: Double,
)

data class CompetitorBenchmark(
    val id: Long,
    val channelName: String,
    val platform: String,
    val subscriberCount: Long,
    val totalViews: Long,
    val videoCount: Int,
    val avgViews: Long,
    val engagementRate: Double,
    val growthRate: Double,
    val profileImageUrl: String?,
)
