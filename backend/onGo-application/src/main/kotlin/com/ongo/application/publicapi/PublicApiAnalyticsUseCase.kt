package com.ongo.application.publicapi

import com.ongo.application.analytics.AnalyticsUseCase
import com.ongo.application.analytics.PlatformMetricAvailability
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
        /*
         * **플랫폼을 잃지 않고 펼친다.** 예전에는 `flatMap { it.dailyData }` 로 합쳐
         * 어느 플랫폼의 행인지 알 수 없었고, Pinterest 의 저장 수가 좋아요로 나갔다.
         */
        val daily = details.platforms.flatMap { detail ->
            detail.dailyData.map { detail.platform.name to it }
        }
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
        // 업로드 ID → 플랫폼. 행에는 `videoUploadId` 만 있어 이 매핑 없이는 판정할 수 없다.
        val platformByUploadId = channelUploads
            .mapNotNull { upload -> upload.id?.let { it to upload.platform.name } }
            .toMap()
        val data = analyticsRepository
            .findByVideoUploadIdsAndDateRange(channelUploads.mapNotNull { it.id }, from, LocalDate.now())
            .values
            .flatten()
            .mapNotNull { row -> platformByUploadId[row.videoUploadId]?.let { it to row } }
        return metricsFromStored(data)
    }

    private fun metricsFromDaily(data: List<Pair<String, DailyMetric>>): List<PublicAnalyticsMetric> =
        metricsFromRows(data.map { (platform, it) ->
            MetricRow(
                date = it.date,
                platform = platform,
                views = it.views.toLong(),
                likes = it.likes.toLong(),
                comments = it.comments.toLong(),
                shares = it.shares.toLong(),
            )
        })

    private fun metricsFromStored(data: List<Pair<String, AnalyticsDaily>>): List<PublicAnalyticsMetric> =
        metricsFromRows(data.map { (platform, it) ->
            MetricRow(
                date = it.date,
                platform = platform,
                views = it.views.toLong(),
                likes = it.likes.toLong(),
                comments = it.commentsCount.toLong(),
                shares = it.shares.toLong(),
            )
        })

    /**
     * 지표별 시계열. **그 지표를 수집하는 행이 하나도 없으면 지표 자체를 내보내지 않는다.**
     *
     * ## 왜 `0` 이 아니라 생략인가
     *
     * 이 응답은 외부 연동이 읽는 공개 API 다. `total = "0"` 을 주면 소비자는 그것을
     * **측정된 0** 으로 읽고 차트를 그린다. 새 필드(`available` 같은)를 더해도, 그 필드를
     * 모르는 기존 소비자는 여전히 0 을 그린다 — 위장이 그대로 남는다.
     *
     * 목록에서 빼면 **그 지표를 모르는 소비자도 자연히 아무것도 그리지 않는다.** 없는
     * 것을 없다고 말하는 유일한 방법이다.
     *
     * ## 무엇을 걸러 내나
     *
     * `PinterestClient.kt:158` 의 `likes` 는 `SAVE`(저장), `:160` 의 `shares` 는
     * `PIN_CLICK`(클릭), `TumblrClient.kt:141` 의 `views` 는 `total_notes`(노트 총합)다.
     * 하드코딩 0 과 달리 **다른 뜻의 큰 숫자**라 합계에 섞이면 조용히 틀린다.
     */
    private fun metricsFromRows(data: List<MetricRow>): List<PublicAnalyticsMetric> {
        return listOf(
            Triple("Views", PlatformMetricAvailability.VIEWS, MetricRow::views),
            Triple("Likes", PlatformMetricAvailability.LIKES, MetricRow::likes),
            Triple("Comments", PlatformMetricAvailability.COMMENTS, MetricRow::comments),
            Triple("Shares", PlatformMetricAvailability.SHARES, MetricRow::shares),
        ).mapNotNull { (label, metric, selector) ->
            val measured = data.filter { PlatformMetricAvailability.isAvailable(it.platform, metric) }
            if (measured.isEmpty()) return@mapNotNull null

            val points = measured.groupBy { it.date }.toSortedMap().map { (date, rows) ->
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

    /** 행이 **자기 플랫폼을 들고 다닌다.** 그래야 지표별 수집 여부를 판정할 수 있다. */
    private data class MetricRow(
        val date: java.time.LocalDate,
        val platform: String,
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
