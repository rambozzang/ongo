package com.ongo.domain.analytics

import com.ongo.domain.video.Video
import java.time.LocalDate

interface AnalyticsRepository {
    fun findByVideoUploadIdAndDateRange(videoUploadId: Long, from: LocalDate, to: LocalDate): List<AnalyticsDaily>
    fun findByVideoUploadIdsAndDateRange(videoUploadIds: List<Long>, from: LocalDate, to: LocalDate): Map<Long, List<AnalyticsDaily>>
    fun getDashboardKpi(userId: Long, days: Int): DashboardKpi
    fun getTrendData(userId: Long, days: Int): List<TrendData>
    fun getTopVideos(userId: Long, days: Int, limit: Int): List<Video>
    fun getHeatmapData(userId: Long): Map<String, Map<Int, Long>>
    fun save(analytics: AnalyticsDaily): AnalyticsDaily
    fun upsert(analytics: AnalyticsDaily): AnalyticsDaily
    fun saveBatch(analytics: List<AnalyticsDaily>)
    fun findDailyAnalyticsByChannelIds(userId: Long, platform: com.ongo.common.enums.Platform?): List<AnalyticsDaily>
    fun findByVideoUploadIds(uploadIds: List<Long>): List<AnalyticsDaily>
    fun findAllByUserId(userId: Long): List<AnalyticsDaily>
    fun upsertChannelInsights(insights: ChannelInsightsDaily)
    fun findChannelInsights(userId: Long, platform: com.ongo.common.enums.Platform?, startDate: LocalDate, endDate: LocalDate): List<ChannelInsightsDaily>
    fun findCrossPlatformMetrics(userId: Long, days: Int): List<CrossPlatformRaw>
}

/**
 * 크로스 플랫폼 분석용 원시 데이터.
 * 영상별 + 플랫폼별로 집계된 analytics 데이터를 담는다.
 */
data class CrossPlatformRaw(
    val videoId: Long,
    val videoTitle: String?,
    val platform: String,
    val videoUploadId: Long,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val revenueMicro: Long,
    val impressions: Long,
    val avgViewDurationSeconds: Long,
)
