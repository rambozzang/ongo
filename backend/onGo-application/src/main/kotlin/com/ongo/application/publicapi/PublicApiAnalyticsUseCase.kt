package com.ongo.application.publicapi

import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.application.analytics.dto.DailyMetric
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.publicapi.PublicApiPostRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

/** Postiz public analytics 응답을 onGo의 누적 analytics 저장소에서 만든다. */
@Service
class PublicApiAnalyticsUseCase(
    private val postRepository: PublicApiPostRepository,
    private val analyticsUseCase: AnalyticsUseCase,
    private val analyticsRepository: AnalyticsRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {
    fun post(userId: Long, postId: Long, days: Int): List<PublicAnalyticsMetric> {
        val post = postRepository.findByIdAndUserId(postId, userId)
            ?: throw NotFoundException("공개 API 게시", postId)
        val details = analyticsUseCase.getVideoAnalytics(userId, post.videoId, days.coerceIn(1, 365))
        val daily = details.platforms.flatMap { it.dailyData }
        return metricsFromDaily(daily)
    }

    fun platform(userId: Long, integrationId: String, days: Int): List<PublicAnalyticsMetric> {
        val channelId = integrationId.toLongOrNull()
            ?: throw IllegalArgumentException("integration id는 onGo 채널 ID여야 합니다")
        val channelUploads = videoUploadRepository.findByUserId(userId)
            .filter { it.channelId == channelId }
        if (channelUploads.isEmpty()) return emptyList()

        val safeDays = days.coerceIn(1, 365)
        val from = LocalDate.now().minusDays(safeDays.toLong())
        val data = analyticsRepository
            .findByVideoUploadIdsAndDateRange(channelUploads.mapNotNull { it.id }, from, LocalDate.now())
            .values
            .flatten()
        return metricsFromStored(data)
    }

    private fun metricsFromDaily(data: List<DailyMetric>): List<PublicAnalyticsMetric> =
        metricsFromRows(data.map {
            MetricRow(
                date = it.date,
                views = it.views.toLong(),
                likes = it.likes.toLong(),
                comments = it.comments.toLong(),
                shares = 0,
            )
        })

    private fun metricsFromStored(data: List<AnalyticsDaily>): List<PublicAnalyticsMetric> =
        metricsFromRows(data.map {
            MetricRow(
                date = it.date,
                views = it.views.toLong(),
                likes = it.likes.toLong(),
                comments = it.commentsCount.toLong(),
                shares = it.shares.toLong(),
            )
        })

    private fun metricsFromRows(data: List<MetricRow>): List<PublicAnalyticsMetric> {
        val byDate = data.groupBy { it.date }.toSortedMap()
        return listOf(
            "Views" to MetricRow::views,
            "Likes" to MetricRow::likes,
            "Comments" to MetricRow::comments,
            "Shares" to MetricRow::shares,
        ).map { (label, selector) ->
            val points = byDate.map { (date, rows) ->
                PublicAnalyticsPoint(
                    total = rows.sumOf(selector).toString(),
                    date = date.toString(),
                )
            }
            PublicAnalyticsMetric(
                label = label,
                data = points,
                percentageChange = percentageChange(points),
            )
        }
    }

    private fun percentageChange(points: List<PublicAnalyticsPoint>): Double? {
        if (points.size < 2) return null
        val midpoint = points.size / 2
        val previous = points.take(midpoint).sumOf { it.total.toLongOrNull() ?: 0L }
        val current = points.drop(midpoint).sumOf { it.total.toLongOrNull() ?: 0L }
        if (previous == 0L) return null
        return ((current - previous).toDouble() / previous.toDouble() * 100.0)
            .let { kotlin.math.round(it * 10) / 10.0 }
    }

    private data class MetricRow(
        val date: java.time.LocalDate,
        val views: Long,
        val likes: Long,
        val comments: Long,
        val shares: Long,
    )
}

data class PublicAnalyticsMetric(
    val label: String,
    val data: List<PublicAnalyticsPoint>,
    val percentageChange: Double?,
)

data class PublicAnalyticsPoint(
    val total: String,
    val date: String,
)
