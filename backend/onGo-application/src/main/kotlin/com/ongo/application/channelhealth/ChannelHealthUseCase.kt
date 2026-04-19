package com.ongo.application.channelhealth

import com.ongo.application.channelhealth.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channelhealth.ChannelHealthMetric
import com.ongo.domain.channelhealth.ChannelHealthMetricRepository
import com.ongo.domain.channelhealth.HealthTrend
import com.ongo.domain.channelhealth.HealthTrendRepository
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Service
class ChannelHealthUseCase(
    private val metricRepository: ChannelHealthMetricRepository,
    private val trendRepository: HealthTrendRepository,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
) {

    fun getMetrics(userId: Long): List<ChannelHealthMetricResponse> {
        return metricRepository.findByUserId(userId).map { it.toResponse() }
    }

    /**
     * 채널 헬스 스코어를 실제 데이터 기반으로 계산.
     *
     * 5축 (각 0~100):
     *  - growth: 최근 7일 신규 구독자(가중치 10명=10점, 100명=100점)
     *  - engagement: (좋아요+댓글+공유)/조회수 * 1000 (3=30점, 10=100점)
     *  - consistency: 최근 30일 업로드 수 (8개=100점, 0개=0점)
     *  - audience: 평균 시청 시간(초) (180s=100점)
     *  - monetization: 최근 30일 수익 발생 여부(KRW 10만=100점)
     */
    fun measure(userId: Long, channelId: Long): ChannelHealthMetricResponse {
        val channel = channelRepository.findByUserId(userId)
            .firstOrNull { it.id == channelId }
            ?: throw NotFoundException("채널", channelId)

        val today = LocalDate.now()
        val daily = analyticsRepository.getDailyAggregates(userId, today.minusDays(30), today)

        val totalViews = daily.sumOf { it.views }
        val totalLikes = daily.sumOf { it.likes }
        val totalComments = daily.sumOf { it.comments }
        val totalShares = daily.sumOf { it.shares }
        val totalWatch = daily.sumOf { it.watchTimeSeconds }
        val recentSubs = daily.takeLast(7).sumOf { it.subscriberGained }
        val totalRevenueMicro = daily.sumOf { it.revenueMicro }

        val uploadsThisMonth = videoRepository.countByUserIdAndMonth(userId, YearMonth.now())

        val growthScore = scoreLinear(recentSubs.toDouble(), max = 100.0)
        val engagementRate = if (totalViews > 0) {
            ((totalLikes + totalComments + totalShares).toDouble() / totalViews) * 1000.0
        } else 0.0
        val engagementScore = scoreLinear(engagementRate, max = 10.0)
        val consistencyScore = scoreLinear(uploadsThisMonth.toDouble(), max = 8.0)
        val avgViewSec = if (totalViews > 0) totalWatch.toDouble() / totalViews else 0.0
        val audienceScore = scoreLinear(avgViewSec, max = 180.0)
        val revenueKrw = totalRevenueMicro / 1_000_000.0
        val monetizationScore = scoreLinear(revenueKrw, max = 100_000.0)

        val overall = listOf(growthScore, engagementScore, consistencyScore, audienceScore, monetizationScore)
            .average()
            .roundToInt()

        val metric = ChannelHealthMetric(
            userId = userId,
            channelId = channelId,
            channelName = channel.channelName,
            platform = channel.platform.name,
            overallScore = overall,
            growthScore = growthScore,
            engagementScore = engagementScore,
            consistencyScore = consistencyScore,
            audienceScore = audienceScore,
            monetizationScore = monetizationScore,
        )
        return metricRepository.save(metric).toResponse()
    }

    /** 0..max 값을 0..100 점수로 선형 매핑 (포화) */
    private fun scoreLinear(value: Double, max: Double): Int {
        if (max <= 0) return 0
        val ratio = min(1.0, max(0.0, value / max))
        return (ratio * 100).roundToInt()
    }

    fun getTrends(metricId: Long): List<HealthTrendResponse> {
        return trendRepository.findByMetricId(metricId).map { it.toTrendResponse() }
    }

    fun getSummary(userId: Long): ChannelHealthSummaryResponse {
        val metrics = metricRepository.findByUserId(userId)
        val avgScore = if (metrics.isNotEmpty()) metrics.map { it.overallScore }.average().toInt() else 0
        val topChannel = metrics.maxByOrNull { it.overallScore }?.channelName ?: ""
        val scores = metrics.flatMap { listOf(it.growthScore, it.engagementScore, it.consistencyScore, it.audienceScore, it.monetizationScore) }
        val weakest = listOf("GROWTH", "ENGAGEMENT", "CONSISTENCY", "AUDIENCE", "MONETIZATION")
            .zip(listOf(
                metrics.map { it.growthScore }.average(),
                metrics.map { it.engagementScore }.average(),
                metrics.map { it.consistencyScore }.average(),
                metrics.map { it.audienceScore }.average(),
                metrics.map { it.monetizationScore }.average(),
            )).minByOrNull { it.second }?.first ?: ""
        return ChannelHealthSummaryResponse(
            totalChannels = metrics.size,
            avgOverallScore = avgScore,
            topChannel = topChannel,
            weakestArea = weakest,
            trendsUp = 0,
            trendsDown = 0,
        )
    }

    private fun ChannelHealthMetric.toResponse() = ChannelHealthMetricResponse(
        id = id!!,
        channelId = channelId,
        channelName = channelName,
        platform = platform,
        overallScore = overallScore,
        growthScore = growthScore,
        engagementScore = engagementScore,
        consistencyScore = consistencyScore,
        audienceScore = audienceScore,
        monetizationScore = monetizationScore,
        measuredAt = measuredAt,
    )

    private fun HealthTrend.toTrendResponse() = HealthTrendResponse(
        id = id!!,
        metricId = metricId,
        category = category,
        date = trendDate,
        score = score,
        change = changeValue,
        recommendation = recommendation,
    )
}
