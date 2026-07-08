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

    /** 해당 upload의 가장 최근 동기화된 날짜를 반환 (없으면 null) */
    fun findLatestDateByVideoUploadId(videoUploadId: Long): LocalDate?
    fun upsertChannelInsights(insights: ChannelInsightsDaily)
    fun findChannelInsights(userId: Long, platform: com.ongo.common.enums.Platform?, startDate: LocalDate, endDate: LocalDate): List<ChannelInsightsDaily>
    fun findCrossPlatformMetrics(userId: Long, days: Int): List<CrossPlatformRaw>

    /**
     * 라이브 대시보드용: 기간별 일별 집계 데이터 (조회수, 좋아요, 댓글, 시청시간, 구독자, 수익)
     */
    fun getDailyAggregates(userId: Long, from: LocalDate, to: LocalDate): List<DailyAggregate>

    /**
     * 크로스 분석용: 영상별 + 플랫폼별 상세 메트릭 (thumbnailUrl, publishedAt 포함)
     */
    fun findCrossPlatformDetailMetrics(userId: Long, days: Int): List<CrossPlatformDetailRaw>
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

/**
 * 라이브 대시보드용 일별 집계 데이터
 */
data class DailyAggregate(
    val date: LocalDate,
    val views: Long,
    val likes: Long,
    val comments: Long,
    val shares: Long,
    val watchTimeSeconds: Long,
    val subscriberGained: Long,
    val revenueMicro: Long,
)

/**
 * 크로스 분석용 영상별 + 플랫폼별 상세 데이터 (썸네일, 게시일 포함)
 */
data class CrossPlatformDetailRaw(
    val videoId: Long,
    val videoTitle: String?,
    val thumbnailUrls: List<String>,
    val publishedAt: java.time.LocalDateTime?,
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
