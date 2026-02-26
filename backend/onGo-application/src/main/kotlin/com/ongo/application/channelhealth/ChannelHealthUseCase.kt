package com.ongo.application.channelhealth

import com.ongo.application.channelhealth.dto.*
import com.ongo.domain.channelhealth.ChannelHealthMetric
import com.ongo.domain.channelhealth.ChannelHealthMetricRepository
import com.ongo.domain.channelhealth.HealthTrend
import com.ongo.domain.channelhealth.HealthTrendRepository
import org.springframework.stereotype.Service

@Service
class ChannelHealthUseCase(
    private val metricRepository: ChannelHealthMetricRepository,
    private val trendRepository: HealthTrendRepository,
) {

    fun getMetrics(userId: Long): List<ChannelHealthMetricResponse> {
        return metricRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun measure(userId: Long, channelId: Long): ChannelHealthMetricResponse {
        val metric = ChannelHealthMetric(
            userId = userId,
            channelId = channelId,
            channelName = "채널",
            platform = "YOUTUBE",
            overallScore = 75,
            growthScore = 70,
            engagementScore = 80,
            consistencyScore = 75,
            audienceScore = 72,
            monetizationScore = 68,
        )
        return metricRepository.save(metric).toResponse()
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
