package com.ongo.application.crossanalytics.dto

import java.time.LocalDateTime

/**
 * 크로스 플랫폼 분석 응답 - 프론트엔드 CrossPlatformReport 타입과 매핑.
 * contents, platformSummaries, audienceOverlap, recommendations 가 구조화된 객체로 반환됨.
 */
data class CrossAnalyticsResponse(
    val id: Long,
    val period: String,
    val contents: List<CrossContentItem>,
    val platformSummaries: List<PlatformPerformanceSummary>,
    val audienceOverlap: List<AudienceOverlapItem>,
    val recommendations: List<String>,
    val generatedAt: String?,
)

/**
 * 콘텐츠별 크로스 플랫폼 데이터.
 * 프론트엔드 CrossPlatformContent 타입과 매핑.
 */
data class CrossContentItem(
    val id: Long,
    val title: String,
    val thumbnailUrl: String,
    val publishedAt: String?,
    val platforms: List<ContentPlatformMetrics>,
)

/**
 * 특정 콘텐츠의 플랫폼별 지표
 */
data class ContentPlatformMetrics(
    val platform: String,
    val videoId: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val avgWatchTime: Long,
    val ctr: Double,
    val publishedAt: String?,
)

/**
 * 플랫폼 성과 요약
 */
data class PlatformPerformanceSummary(
    val platform: String,
    val totalViews: Long,
    val totalLikes: Long,
    val avgEngagement: Double,
    val avgCtr: Double,
    val contentCount: Int,
    val bestPerformingContentId: Long,
)

/**
 * 오디언스 중복 분석
 */
data class AudienceOverlapItem(
    val platforms: List<String>,
    val overlapPercent: Int,
)

// ── 기존 호환용 DTOs ──────────────────────────────────────────────

/**
 * DB 저장/조회용 리포트 응답 (JSON 문자열 기반, 리포트 CRUD용)
 */
data class CrossPlatformReportResponse(
    val id: Long,
    val period: String,
    val contents: String,
    val platformSummaries: String,
    val audienceOverlap: String,
    val recommendations: String,
    val generatedAt: LocalDateTime?,
)

data class GenerateReportRequest(
    val period: String,
    val contents: String = "[]",
    val platformSummaries: String = "[]",
    val audienceOverlap: String = "{}",
    val recommendations: String = "[]",
)

data class ContentCompareRequest(
    val videoIds: List<Long> = emptyList(),
    val period: String = "30d",
)

data class CrossPlatformContent(
    val videoId: Long,
    val title: String,
    val platform: String,
    val views: Long,
    val likes: Long,
    val comments: Long,
)

data class ContentCompareResponse(
    val contents: List<CrossPlatformContent>,
)
