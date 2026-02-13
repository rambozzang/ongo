package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.enums.Platform
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.credit.CreditRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class AnalyticsUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val userRepository: UserRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val creditRepository: CreditRepository
) {

    @Cacheable(value = ["dashboardKpi"], key = "#userId + '-' + #days")
    fun getDashboardKpi(userId: Long, days: Int): DashboardKpiResponse {
        val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
        val kpi = analyticsRepository.getDashboardKpi(userId, days)
        val credit = creditRepository.findByUserId(userId)

        return DashboardKpiResponse(
            totalViews = kpi.totalViews,
            viewsChangePercent = kpi.totalViewsChange,
            totalSubscribers = kpi.totalSubscribers,
            subscribersChange = kpi.totalSubscribersChange,
            totalLikes = kpi.totalLikes,
            likesChangePercent = kpi.totalLikesChange,
            creditBalance = credit?.balance ?: 0,
            creditTotal = credit?.freeMonthly ?: user.planType.freeCredits
        )
    }

    @Cacheable(value = ["trendData"], key = "#userId + '-' + #days")
    fun getTrends(userId: Long, days: Int): TrendDataResponse {
        val trendData = analyticsRepository.getTrendData(userId, days)
        val grouped = trendData.groupBy { it.date }

        val points = grouped.map { (date, items) ->
            val platformViews = items
                .filter { it.platform != null }
                .associate { it.platform!!.name to it.views }
            TrendPoint(
                date = date,
                totalViews = items.sumOf { it.views },
                platformViews = platformViews
            )
        }.sortedBy { it.date }

        return TrendDataResponse(data = points)
    }

    fun getVideoAnalytics(userId: Long, videoId: Long, days: Int): VideoAnalyticsResponse {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        val uploads = videoUploadRepository.findByVideoId(videoId)
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()

        // Batch fetch all analytics data for all uploads (eliminates N+1)
        val uploadIds = uploads.mapNotNull { it.id }
        val analyticsByUploadId = analyticsRepository.findByVideoUploadIdsAndDateRange(uploadIds, from, to)

        val platforms = uploads.map { upload ->
            val dailyData = analyticsByUploadId[upload.id!!] ?: emptyList()
            PlatformAnalyticsDetail(
                platform = upload.platform,
                views = dailyData.sumOf { it.views.toLong() },
                likes = dailyData.sumOf { it.likes.toLong() },
                comments = dailyData.sumOf { it.commentsCount.toLong() },
                shares = dailyData.sumOf { it.shares.toLong() },
                dailyData = dailyData.map { DailyMetric(it.date, it.views, it.likes, it.commentsCount) }
            )
        }

        return VideoAnalyticsResponse(videoId = videoId, title = video.title, platforms = platforms)
    }

    fun getHeatmap(userId: Long): HeatmapResponse {
        val data = analyticsRepository.getHeatmapData(userId)
        return HeatmapResponse(data = data)
    }

    fun getTopVideos(userId: Long, days: Int, limit: Int): TopVideoResponse {
        val topVideos = analyticsRepository.getTopVideos(userId, days, limit)

        // Batch fetch all uploads for top videos (eliminates N+1)
        val videoIds = topVideos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        val items = topVideos.map { video ->
            val videoId = video.id!!
            val uploads = uploadsByVideoId[videoId] ?: emptyList()
            TopVideoItem(
                id = videoId,
                title = video.title,
                thumbnailUrl = video.thumbnailUrls.firstOrNull(),
                totalViews = 0, // populated from aggregate query
                platforms = uploads.map { it.platform.name }
            )
        }
        return TopVideoResponse(videos = items)
    }

    fun getVideoComparison(userId: Long, videoIds: List<Long>, days: Int): VideoCompareResponse {
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()

        // Batch fetch all videos and uploads (eliminates N+1)
        val videos = videoRepository.findByIds(videoIds).associateBy { it.id!! }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        // Batch fetch all analytics
        val allUploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }
        val analyticsByUploadId = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, from, to)

        val items = videoIds.map { videoId ->
            val video = videos[videoId] ?: throw NotFoundException("영상", videoId)
            val uploads = uploadsByVideoId[videoId] ?: emptyList()
            val uploadIds = uploads.mapNotNull { it.id }

            val allAnalytics = uploadIds.flatMap { analyticsByUploadId[it] ?: emptyList() }
            val totalViews = allAnalytics.sumOf { it.views.toLong() }
            val totalLikes = allAnalytics.sumOf { it.likes.toLong() }
            val totalComments = allAnalytics.sumOf { it.commentsCount.toLong() }
            val totalShares = allAnalytics.sumOf { it.shares.toLong() }
            val totalWatchTime = allAnalytics.sumOf { it.watchTimeSeconds }
            val dayCount = allAnalytics.map { it.date }.distinct().size.coerceAtLeast(1)
            val engagementRate = if (totalViews > 0) {
                ((totalLikes + totalComments + totalShares).toDouble() / totalViews) * 100
            } else 0.0

            VideoCompareItem(
                videoId = videoId,
                title = video.title,
                totalViews = totalViews,
                totalLikes = totalLikes,
                totalComments = totalComments,
                totalShares = totalShares,
                totalWatchTimeSeconds = totalWatchTime,
                avgDailyViews = totalViews / dayCount,
                engagementRate = Math.round(engagementRate * 100) / 100.0,
            )
        }

        return VideoCompareResponse(videos = items)
    }

    @Cacheable(value = ["optimalTimes"], key = "#userId + '-' + #platform")
    fun getOptimalPublishTimes(userId: Long, platform: Platform?): OptimalTimesResponse {
        val dailyData = analyticsRepository.findDailyAnalyticsByChannelIds(userId, platform)
        if (dailyData.isEmpty()) return OptimalTimesResponse(slots = emptyList())

        val now = LocalDate.now()
        val thirtyDaysAgo = now.minusDays(30)

        val dayLabels = arrayOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")

        // Group by dayOfWeek × hour
        data class SlotKey(val dayOfWeek: Int, val hour: Int)
        data class SlotData(val views: MutableList<Int> = mutableListOf(), val engagements: MutableList<Double> = mutableListOf(), var recentCount: Int = 0, var totalCount: Int = 0)

        val slotMap = mutableMapOf<SlotKey, SlotData>()

        for (record in dailyData) {
            val dow = record.date.dayOfWeek.value % 7 // Convert to 0=Sun
            // Use hour from createdAt if available, otherwise distribute across common hours
            val hour = record.createdAt?.hour ?: 12
            val key = SlotKey(dow, hour)
            val data = slotMap.getOrPut(key) { SlotData() }

            val isRecent = !record.date.isBefore(thirtyDaysAgo)
            val weight = if (isRecent) 3 else 1

            repeat(weight) {
                data.views.add(record.views)
            }

            val totalEngagements = record.likes + record.commentsCount + record.shares
            val engagementRate = if (record.views > 0) totalEngagements.toDouble() / record.views * 100 else 0.0
            repeat(weight) {
                data.engagements.add(engagementRate)
            }

            if (isRecent) data.recentCount++
            data.totalCount++
        }

        // Calculate median and score for each slot
        val slots = slotMap.map { (key, data) ->
            val medianViews = median(data.views.map { it.toLong() })
            val medianEngagement = medianDouble(data.engagements)
            val confidence = (data.totalCount.coerceAtMost(30).toDouble() / 30.0) * 100.0
            val score = medianViews * 0.6 + medianEngagement * 100 * 0.3 + confidence * 0.1

            OptimalTimeSlot(
                dayOfWeek = key.dayOfWeek,
                dayLabel = dayLabels[key.dayOfWeek],
                hour = key.hour,
                timeLabel = "${key.hour.toString().padStart(2, '0')}:00",
                expectedViews = medianViews,
                engagementRate = Math.round(medianEngagement * 100) / 100.0,
                confidenceScore = Math.round(confidence * 100) / 100.0,
                score = Math.round(score * 100) / 100.0,
            )
        }
            .sortedByDescending { it.score }
            .take(5)

        return OptimalTimesResponse(slots = slots)
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private fun medianDouble(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    fun getTagPerformance(userId: Long, days: Int): TagPerformanceResponse {
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()
        val previousFrom = from.minusDays(days.toLong())

        // Get all user's videos
        val videos = videoRepository.findByUserId(userId, 0, 1000)
        if (videos.isEmpty()) return TagPerformanceResponse(tags = emptyList())

        // Group videos by tags
        val tagVideoMap = mutableMapOf<String, MutableList<Video>>()
        for (video in videos) {
            for (tag in video.tags) {
                tagVideoMap.getOrPut(tag) { mutableListOf() }.add(video)
            }
        }

        // Batch fetch all uploads
        val allVideoIds = videos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(allVideoIds)
        val allUploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }

        // Batch fetch analytics for current and previous periods
        val currentAnalytics = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, from, to)
        val previousAnalytics = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, previousFrom, from.minusDays(1))

        val tagItems = tagVideoMap.map { (tag, tagVideos) ->
            val tagVideoIds = tagVideos.mapNotNull { it.id }
            val tagUploadIds = tagVideoIds.flatMap { uploadsByVideoId[it]?.mapNotNull { u -> u.id } ?: emptyList() }

            val currentData = tagUploadIds.flatMap { currentAnalytics[it] ?: emptyList() }
            val previousData = tagUploadIds.flatMap { previousAnalytics[it] ?: emptyList() }

            val totalViews = currentData.sumOf { it.views.toLong() }
            val totalLikes = currentData.sumOf { it.likes.toLong() }
            val videoCount = tagVideos.size
            val avgViews = if (videoCount > 0) totalViews / videoCount else 0L
            val avgEngagement = if (totalViews > 0) {
                (totalLikes.toDouble() / totalViews) * 100
            } else 0.0

            val prevViews = previousData.sumOf { it.views.toLong() }
            val trend = when {
                prevViews == 0L && totalViews > 0 -> "up"
                prevViews == 0L -> "stable"
                totalViews > prevViews * 1.1 -> "up"
                totalViews < prevViews * 0.9 -> "down"
                else -> "stable"
            }

            TagPerformanceItem(
                tag = tag,
                videoCount = videoCount,
                totalViews = totalViews,
                totalLikes = totalLikes,
                avgViews = avgViews,
                avgEngagement = Math.round(avgEngagement * 100) / 100.0,
                trend = trend,
            )
        }.sortedByDescending { it.totalViews }

        return TagPerformanceResponse(tags = tagItems)
    }

    fun getPlatformComparison(userId: Long, days: Int): PlatformComparisonResponse {
        val trendData = analyticsRepository.getTrendData(userId, days)
        val byPlatform = trendData.filter { it.platform != null }.groupBy { it.platform!! }
        val summaries = byPlatform.map { (platform, data) ->
            PlatformSummary(
                platform = platform,
                views = data.sumOf { it.views },
                likes = 0, comments = 0, shares = 0
            )
        }
        return PlatformComparisonResponse(platforms = summaries)
    }
}
