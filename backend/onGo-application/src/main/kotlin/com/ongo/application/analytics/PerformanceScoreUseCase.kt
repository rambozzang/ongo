package com.ongo.application.analytics

import com.ongo.application.analytics.dto.AnomalyListResponse
import com.ongo.application.analytics.dto.AnomalyResponse
import com.ongo.application.analytics.dto.PerformanceScoreResponse
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.AnomalyType
import com.ongo.domain.analytics.VideoPerformanceScore
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.sqrt

@Service
class PerformanceScoreUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {

    companion object {
        private const val VIEW_VELOCITY_WEIGHT = 0.30
        private const val ENGAGEMENT_WEIGHT = 0.25
        private const val WATCH_TIME_WEIGHT = 0.20
        private const val CONVERSION_WEIGHT = 0.15
        private const val SHARE_WEIGHT = 0.10
        private const val ANOMALY_Z_THRESHOLD = 2.0
    }

    fun getPerformanceScore(userId: Long, videoId: Long): PerformanceScoreResponse {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }
        val uploads = videoUploadRepository.findByVideoId(videoId)
        val uploadIds = uploads.mapNotNull { it.id }

        if (uploadIds.isEmpty()) {
            return emptyScoreResponse(videoId)
        }

        // Get analytics for this video
        val videoAnalytics = analyticsRepository.findByVideoUploadIds(uploadIds)

        // Get all channel analytics for percentile/anomaly calculation
        val allChannelAnalytics = analyticsRepository.findAllByUserId(userId)

        val score = calculateScore(videoId, videoAnalytics, allChannelAnalytics)

        // Calculate percentile rank
        val allVideoIds = videoUploadRepository.findByUserId(userId)
            .groupBy { it.videoId }
            .keys
        val allScores = allVideoIds.map { vid ->
            val vidUploads = videoUploadRepository.findByVideoId(vid)
            val vidUploadIds = vidUploads.mapNotNull { it.id }
            val vidAnalytics = analyticsRepository.findByVideoUploadIds(vidUploadIds)
            calculateOverallScore(vidAnalytics)
        }.sorted()

        val percentileRank = if (allScores.isNotEmpty()) {
            val rank = allScores.count { it <= score.overallScore }
            (rank.toDouble() / allScores.size) * 100
        } else 100.0

        // Determine trend from last 7 days
        val trend = calculateTrend(videoAnalytics)

        val anomalyDescription = if (score.isAnomaly) {
            getAnomalyDescription(score.anomalyType)
        } else null

        return PerformanceScoreResponse(
            videoId = videoId,
            overallScore = Math.round(score.overallScore * 10) / 10.0,
            breakdown = mapOf(
                "viewVelocity" to Math.round(score.viewVelocityScore * 10) / 10.0,
                "engagement" to Math.round(score.engagementScore * 10) / 10.0,
                "watchTime" to Math.round(score.watchTimeScore * 10) / 10.0,
                "conversion" to Math.round(score.conversionScore * 10) / 10.0,
                "share" to Math.round(score.shareScore * 10) / 10.0,
            ),
            percentileRank = Math.round(percentileRank * 10) / 10.0,
            trend = trend,
            isAnomaly = score.isAnomaly,
            anomalyDescription = anomalyDescription,
            prediction7d = score.predictedViews7d,
        )
    }

    fun getAnomalies(userId: Long): AnomalyListResponse {
        val allVideoIds = videoUploadRepository.findByUserId(userId)
            .groupBy { it.videoId }
            .keys
        val allChannelAnalytics = analyticsRepository.findAllByUserId(userId)

        val anomalies = mutableListOf<AnomalyResponse>()

        for (vid in allVideoIds) {
            val video = videoRepository.findById(vid) ?: continue
            val uploads = videoUploadRepository.findByVideoId(vid)
            val uploadIds = uploads.mapNotNull { it.id }
            val videoAnalytics = analyticsRepository.findByVideoUploadIds(uploadIds)

            val score = calculateScore(vid, videoAnalytics, allChannelAnalytics)
            val detectedAnomalyType = score.anomalyType
            if (score.isAnomaly && detectedAnomalyType != null) {
                val severity = when {
                    score.overallScore >= 90 -> "critical"
                    score.overallScore >= 70 -> "warning"
                    else -> "info"
                }
                anomalies.add(
                    AnomalyResponse(
                        videoId = vid,
                        videoTitle = video.title,
                        anomalyType = detectedAnomalyType,
                        severity = severity,
                        description = getAnomalyDescription(detectedAnomalyType) ?: "",
                        detectedAt = score.calculatedAt,
                    )
                )
            }
        }

        return AnomalyListResponse(anomalies = anomalies)
    }

    private fun calculateScore(
        videoId: Long,
        videoAnalytics: List<AnalyticsDaily>,
        allChannelAnalytics: List<AnalyticsDaily>,
    ): VideoPerformanceScore {
        if (videoAnalytics.isEmpty()) {
            return VideoPerformanceScore(
                videoId = videoId,
                overallScore = 0.0,
                viewVelocityScore = 0.0,
                engagementScore = 0.0,
                watchTimeScore = 0.0,
                conversionScore = 0.0,
                shareScore = 0.0,
            )
        }

        val viewVelocityScore = calculateViewVelocityScore(videoAnalytics, allChannelAnalytics)
        val engagementScore = calculateEngagementScore(videoAnalytics)
        val watchTimeScore = calculateWatchTimeScore(videoAnalytics, allChannelAnalytics)
        val conversionScore = calculateConversionScore(videoAnalytics, allChannelAnalytics)
        val shareScore = calculateShareScore(videoAnalytics, allChannelAnalytics)

        val overallScore = (viewVelocityScore * VIEW_VELOCITY_WEIGHT +
                engagementScore * ENGAGEMENT_WEIGHT +
                watchTimeScore * WATCH_TIME_WEIGHT +
                conversionScore * CONVERSION_WEIGHT +
                shareScore * SHARE_WEIGHT)

        // Anomaly detection using z-score
        val allVideoOverallScores = calculateAllVideoScores(allChannelAnalytics)
        val (isAnomaly, anomalyType) = detectAnomaly(overallScore, allVideoOverallScores, videoAnalytics)

        // 7-day prediction using linear regression
        val predictedViews7d = predictViews7d(videoAnalytics)

        return VideoPerformanceScore(
            videoId = videoId,
            overallScore = overallScore.coerceIn(0.0, 100.0),
            viewVelocityScore = viewVelocityScore.coerceIn(0.0, 100.0),
            engagementScore = engagementScore.coerceIn(0.0, 100.0),
            watchTimeScore = watchTimeScore.coerceIn(0.0, 100.0),
            conversionScore = conversionScore.coerceIn(0.0, 100.0),
            shareScore = shareScore.coerceIn(0.0, 100.0),
            isAnomaly = isAnomaly,
            anomalyType = anomalyType,
            predictedViews7d = predictedViews7d,
        )
    }

    private fun calculateViewVelocityScore(
        videoAnalytics: List<AnalyticsDaily>,
        allAnalytics: List<AnalyticsDaily>,
    ): Double {
        // Views in first 48h (first 2 days) normalized against channel average
        val first48h = videoAnalytics.sortedBy { it.date }.take(2)
        val videoViews48h = first48h.sumOf { it.views }

        // Channel average views in first 2 days
        val channelAvgViews = if (allAnalytics.isNotEmpty()) {
            allAnalytics.sumOf { it.views }.toDouble() / allAnalytics.size.coerceAtLeast(1)
        } else 1.0

        val ratio = videoViews48h / channelAvgViews.coerceAtLeast(1.0)
        return (ratio * 50).coerceIn(0.0, 100.0)
    }

    private fun calculateEngagementScore(videoAnalytics: List<AnalyticsDaily>): Double {
        val totalViews = videoAnalytics.sumOf { it.views }.coerceAtLeast(1)
        val totalLikes = videoAnalytics.sumOf { it.likes }
        val totalComments = videoAnalytics.sumOf { it.commentsCount }
        val totalShares = videoAnalytics.sumOf { it.shares }
        val engagementRate = (totalLikes + totalComments + totalShares).toDouble() / totalViews
        // Scale: 10% engagement rate = 100 score
        return (engagementRate * 1000).coerceIn(0.0, 100.0)
    }

    private fun calculateWatchTimeScore(
        videoAnalytics: List<AnalyticsDaily>,
        allAnalytics: List<AnalyticsDaily>,
    ): Double {
        val avgWatchTime = if (videoAnalytics.isNotEmpty()) {
            videoAnalytics.sumOf { it.watchTimeSeconds }.toDouble() / videoAnalytics.size
        } else 0.0

        val channelAvgWatchTime = if (allAnalytics.isNotEmpty()) {
            allAnalytics.sumOf { it.watchTimeSeconds }.toDouble() / allAnalytics.size
        } else 1.0

        val ratio = avgWatchTime / channelAvgWatchTime.coerceAtLeast(1.0)
        return (ratio * 50).coerceIn(0.0, 100.0)
    }

    private fun calculateConversionScore(
        videoAnalytics: List<AnalyticsDaily>,
        allAnalytics: List<AnalyticsDaily>,
    ): Double {
        val totalSubs = videoAnalytics.sumOf { it.subscriberGained }
        val totalViews = videoAnalytics.sumOf { it.views }.coerceAtLeast(1)
        val conversionRate = totalSubs.toDouble() / totalViews

        val channelAvgRate = if (allAnalytics.isNotEmpty()) {
            val channelSubs = allAnalytics.sumOf { it.subscriberGained }
            val channelViews = allAnalytics.sumOf { it.views }.coerceAtLeast(1)
            channelSubs.toDouble() / channelViews
        } else 0.001

        val ratio = conversionRate / channelAvgRate.coerceAtLeast(0.0001)
        return (ratio * 50).coerceIn(0.0, 100.0)
    }

    private fun calculateShareScore(
        videoAnalytics: List<AnalyticsDaily>,
        allAnalytics: List<AnalyticsDaily>,
    ): Double {
        val totalShares = videoAnalytics.sumOf { it.shares }
        val totalViews = videoAnalytics.sumOf { it.views }.coerceAtLeast(1)
        val shareRate = totalShares.toDouble() / totalViews

        val channelAvgShareRate = if (allAnalytics.isNotEmpty()) {
            val channelShares = allAnalytics.sumOf { it.shares }
            val channelViews = allAnalytics.sumOf { it.views }.coerceAtLeast(1)
            channelShares.toDouble() / channelViews
        } else 0.001

        val ratio = shareRate / channelAvgShareRate.coerceAtLeast(0.0001)
        return (ratio * 50).coerceIn(0.0, 100.0)
    }

    private fun calculateOverallScore(analytics: List<AnalyticsDaily>): Double {
        if (analytics.isEmpty()) return 0.0
        val engagement = calculateEngagementScore(analytics)
        val totalViews = analytics.sumOf { it.views }
        val viewScore = (totalViews.toDouble() / 1000).coerceIn(0.0, 100.0)
        return viewScore * 0.5 + engagement * 0.5
    }

    private fun calculateAllVideoScores(allAnalytics: List<AnalyticsDaily>): List<Double> {
        val byUpload = allAnalytics.groupBy { it.videoUploadId }
        return byUpload.values.map { calculateOverallScore(it) }
    }

    private fun detectAnomaly(
        score: Double,
        allScores: List<Double>,
        videoAnalytics: List<AnalyticsDaily>,
    ): Pair<Boolean, AnomalyType?> {
        if (allScores.size < 3) return Pair(false, null)

        val mean = allScores.average()
        val variance = allScores.map { (it - mean) * (it - mean) }.average()
        val stddev = sqrt(variance)
        if (stddev == 0.0) return Pair(false, null)

        val zScore = (score - mean) / stddev

        if (zScore > ANOMALY_Z_THRESHOLD) {
            // Determine anomaly type
            val totalViews = videoAnalytics.sumOf { it.views }
            val totalShares = videoAnalytics.sumOf { it.shares }
            val totalEngagement = videoAnalytics.sumOf { it.likes + it.commentsCount + it.shares }

            val anomalyType = when {
                totalShares > totalViews * 0.1 -> AnomalyType.SHARE_SPIKE
                totalEngagement > totalViews * 0.2 -> AnomalyType.ENGAGEMENT_SURGE
                else -> AnomalyType.VIRAL_SPIKE
            }
            return Pair(true, anomalyType)
        } else if (zScore < -ANOMALY_Z_THRESHOLD) {
            return Pair(true, AnomalyType.UNUSUAL_DROP)
        }

        return Pair(false, null)
    }

    private fun predictViews7d(videoAnalytics: List<AnalyticsDaily>): Long {
        val last7 = videoAnalytics.sortedBy { it.date }.takeLast(7)
        if (last7.size < 2) {
            return last7.sumOf { it.views.toLong() }
        }

        // Simple linear regression
        val n = last7.size
        val xValues = (0 until n).map { it.toDouble() }
        val yValues = last7.map { it.views.toDouble() }

        val xMean = xValues.average()
        val yMean = yValues.average()

        val numerator = xValues.zip(yValues).sumOf { (x, y) -> (x - xMean) * (y - yMean) }
        val denominator = xValues.sumOf { (it - xMean) * (it - xMean) }

        if (denominator == 0.0) {
            return (yMean * 7).toLong().coerceAtLeast(0)
        }

        val slope = numerator / denominator
        val intercept = yMean - slope * xMean

        // Predict next 7 days
        var totalPredicted = 0.0
        for (day in n until n + 7) {
            val predicted = (slope * day + intercept).coerceAtLeast(0.0)
            totalPredicted += predicted
        }

        return totalPredicted.toLong().coerceAtLeast(0)
    }

    private fun calculateTrend(videoAnalytics: List<AnalyticsDaily>): String {
        val sorted = videoAnalytics.sortedBy { it.date }
        if (sorted.size < 4) return "stable"

        val midpoint = sorted.size / 2
        val firstHalfAvg = sorted.take(midpoint).map { it.views }.average()
        val secondHalfAvg = sorted.drop(midpoint).map { it.views }.average()

        return when {
            secondHalfAvg > firstHalfAvg * 1.1 -> "up"
            secondHalfAvg < firstHalfAvg * 0.9 -> "down"
            else -> "stable"
        }
    }

    private fun getAnomalyDescription(type: AnomalyType?): String? = when (type) {
        AnomalyType.VIRAL_SPIKE -> "이 영상의 조회수가 채널 평균 대비 비정상적으로 높습니다. 바이럴 현상이 감지되었습니다."
        AnomalyType.ENGAGEMENT_SURGE -> "이 영상의 참여율(좋아요, 댓글, 공유)이 평균 대비 매우 높습니다."
        AnomalyType.UNUSUAL_DROP -> "이 영상의 성과가 채널 평균 대비 비정상적으로 낮습니다."
        AnomalyType.SHARE_SPIKE -> "이 영상의 공유 수가 비정상적으로 높습니다. 외부 확산이 감지되었습니다."
        null -> null
    }

    private fun emptyScoreResponse(videoId: Long) = PerformanceScoreResponse(
        videoId = videoId,
        overallScore = 0.0,
        breakdown = mapOf(
            "viewVelocity" to 0.0,
            "engagement" to 0.0,
            "watchTime" to 0.0,
            "conversion" to 0.0,
            "share" to 0.0,
        ),
        percentileRank = 0.0,
        trend = "stable",
        isAnomaly = false,
        anomalyDescription = null,
        prediction7d = 0,
    )
}
