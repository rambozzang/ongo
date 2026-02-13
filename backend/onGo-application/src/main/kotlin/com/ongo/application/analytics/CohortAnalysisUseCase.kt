package com.ongo.application.analytics

import com.ongo.application.analytics.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CohortGroupBy {
    CATEGORY,
    TAG,
    PLATFORM,
    UPLOAD_MONTH,
}

@Service
class CohortAnalysisUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {

    @Cacheable(value = ["cohortAnalysis"], key = "#userId + '-' + #groupBy + '-' + #from + '-' + #to")
    fun getCohortAnalysis(
        userId: Long,
        groupBy: CohortGroupBy,
        from: LocalDate?,
        to: LocalDate?,
    ): CohortAnalysisResponse {
        val dateFrom = from ?: LocalDate.now().minusDays(90)
        val dateTo = to ?: LocalDate.now()

        val videos = videoRepository.findByUserId(userId, page = 0, size = 1000)
        if (videos.isEmpty()) {
            return CohortAnalysisResponse(
                groupBy = groupBy.name,
                cohorts = emptyList(),
                dateRange = DateRangeInfo(dateFrom.toString(), dateTo.toString()),
            )
        }

        // Group videos by criteria
        val groupedVideos = groupVideos(videos, groupBy)

        val cohorts = groupedVideos.map { (groupName, groupVideos) ->
            buildCohortGroup(groupName, groupVideos, dateFrom, dateTo)
        }.sortedByDescending { it.avgViews }

        return CohortAnalysisResponse(
            groupBy = groupBy.name,
            cohorts = cohorts,
            dateRange = DateRangeInfo(dateFrom.toString(), dateTo.toString()),
        )
    }

    fun getRetentionCurve(userId: Long, videoId: Long): RetentionCurveResponse {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        val uploads = videoUploadRepository.findByVideoId(videoId)
        val uploadIds = uploads.mapNotNull { it.id }

        if (uploadIds.isEmpty()) {
            return RetentionCurveResponse(
                videoId = videoId,
                retentionPoints = emptyList(),
                avgRetention = emptyList(),
                dropOffPoints = emptyList(),
            )
        }

        // Get all analytics data for this video
        val allAnalytics = analyticsRepository.findByVideoUploadIds(uploadIds)

        if (allAnalytics.isEmpty()) {
            return RetentionCurveResponse(
                videoId = videoId,
                retentionPoints = emptyList(),
                avgRetention = emptyList(),
                dropOffPoints = emptyList(),
            )
        }

        // Build retention curve from daily analytics (simulate retention over video timeline)
        val durationSeconds = video.durationSeconds ?: 300 // default 5 minutes
        val intervalSeconds = maxOf(durationSeconds / 20, 1) // 20 data points across the video

        val totalViews = allAnalytics.sumOf { it.views.toLong() }.coerceAtLeast(1)
        val avgWatchTime = if (allAnalytics.isNotEmpty()) {
            allAnalytics.sumOf { it.watchTimeSeconds } / allAnalytics.size.toLong()
        } else {
            (durationSeconds * 0.45).toLong()
        }

        // Generate retention curve based on watch time / duration ratio
        val retentionPoints = mutableListOf<RetentionDataPoint>()
        for (i in 0..20) {
            val timestamp = i * intervalSeconds
            if (timestamp > durationSeconds) break

            val progressRatio = timestamp.toDouble() / durationSeconds
            // Retention model: exponential decay based on avg watch time
            val watchRatio = avgWatchTime.toDouble() / durationSeconds
            val retention = when {
                progressRatio <= 0.0 -> 100.0
                progressRatio <= 0.1 -> 100.0 - (progressRatio * 50) // initial drop
                else -> {
                    val decayFactor = -1.5 / watchRatio.coerceAtLeast(0.1)
                    90.0 * Math.exp(decayFactor * (progressRatio - 0.1))
                }
            }.coerceIn(0.0, 100.0)

            retentionPoints.add(
                RetentionDataPoint(
                    timestamp = timestamp,
                    retentionRate = Math.round(retention * 100) / 100.0,
                    viewCount = (totalViews * retention / 100).toLong(),
                )
            )
        }

        // Generate channel average retention (slightly different curve)
        val allUserAnalytics = analyticsRepository.findAllByUserId(userId)
        val channelAvgWatchRatio = if (allUserAnalytics.isNotEmpty()) {
            val totalWatch = allUserAnalytics.sumOf { it.watchTimeSeconds }
            val count = allUserAnalytics.size
            (totalWatch.toDouble() / count / durationSeconds).coerceIn(0.1, 1.0)
        } else {
            0.5
        }

        val avgRetention = (0..20).mapNotNull { i ->
            val timestamp = i * intervalSeconds
            if (timestamp > durationSeconds) return@mapNotNull null

            val progressRatio = timestamp.toDouble() / durationSeconds
            val retention = when {
                progressRatio <= 0.0 -> 100.0
                progressRatio <= 0.1 -> 100.0 - (progressRatio * 40)
                else -> {
                    val decayFactor = -1.2 / channelAvgWatchRatio
                    88.0 * Math.exp(decayFactor * (progressRatio - 0.1))
                }
            }.coerceIn(0.0, 100.0)

            RetentionDataPoint(
                timestamp = timestamp,
                retentionRate = Math.round(retention * 100) / 100.0,
                viewCount = 0,
            )
        }

        // Find drop-off points (significant drops in retention)
        val dropOffPoints = mutableListOf<DropOffPoint>()
        for (i in 1 until retentionPoints.size) {
            val prev = retentionPoints[i - 1]
            val curr = retentionPoints[i]
            val dropRate = prev.retentionRate - curr.retentionRate

            if (dropRate > 8.0) {
                val reason = when {
                    curr.timestamp < durationSeconds * 0.1 -> "인트로 구간 이탈"
                    curr.timestamp < durationSeconds * 0.3 -> "초반 콘텐츠 관심도 하락"
                    curr.timestamp < durationSeconds * 0.7 -> "중반 이탈 구간"
                    else -> "후반 자연 이탈"
                }
                dropOffPoints.add(
                    DropOffPoint(
                        timestamp = curr.timestamp,
                        dropRate = Math.round(dropRate * 100) / 100.0,
                        possibleReason = reason,
                    )
                )
            }
        }

        return RetentionCurveResponse(
            videoId = videoId,
            retentionPoints = retentionPoints,
            avgRetention = avgRetention,
            dropOffPoints = dropOffPoints,
        )
    }

    private fun groupVideos(videos: List<Video>, groupBy: CohortGroupBy): Map<String, List<Video>> {
        return when (groupBy) {
            CohortGroupBy.CATEGORY -> videos.groupBy { it.category ?: "미분류" }
            CohortGroupBy.TAG -> {
                // Group by most common tags (each video can appear in multiple groups)
                val tagGroups = mutableMapOf<String, MutableList<Video>>()
                for (video in videos) {
                    val tags = video.tags
                    if (tags.isEmpty()) {
                        tagGroups.getOrPut("태그 없음") { mutableListOf() }.add(video)
                    } else {
                        for (tag in tags.take(3)) { // limit to top 3 tags per video
                            tagGroups.getOrPut(tag) { mutableListOf() }.add(video)
                        }
                    }
                }
                // Keep only groups with at least 2 videos
                tagGroups.filter { it.value.size >= 2 }
                    .entries
                    .sortedByDescending { it.value.size }
                    .take(10)
                    .associate { it.key to it.value }
            }
            CohortGroupBy.PLATFORM -> {
                // We need upload info for platform grouping — use video IDs
                val videoMap = videos.associateBy { it.id!! }
                val result = mutableMapOf<String, MutableList<Video>>()
                for (video in videos) {
                    val uploads = videoUploadRepository.findByVideoId(video.id!!)
                    for (upload in uploads) {
                        result.getOrPut(upload.platform.name) { mutableListOf() }.add(video)
                    }
                }
                result
            }
            CohortGroupBy.UPLOAD_MONTH -> {
                videos.groupBy { video ->
                    val created = video.createdAt
                    if (created != null) "${created.year}-${created.monthValue.toString().padStart(2, '0')}"
                    else "날짜 없음"
                }
            }
        }
    }

    private fun buildCohortGroup(
        name: String,
        videos: List<Video>,
        dateFrom: LocalDate,
        dateTo: LocalDate,
    ): CohortGroupResponse {
        val milestones = listOf(1, 3, 7, 14, 30, 60, 90)
        val cumulativeMap = mutableMapOf<Int, Long>()

        for (video in videos) {
            val videoCreated = video.createdAt?.toLocalDate() ?: continue
            val uploads = videoUploadRepository.findByVideoId(video.id!!)
            val uploadIds = uploads.mapNotNull { it.id }
            if (uploadIds.isEmpty()) continue

            for (day in milestones) {
                val endDate = videoCreated.plusDays(day.toLong())
                if (endDate.isAfter(dateTo)) continue

                val analyticsForPeriod = analyticsRepository.findByVideoUploadIdsAndDateRange(
                    uploadIds, videoCreated, endDate,
                )
                val views = analyticsForPeriod.values.flatten().sumOf { it.views.toLong() }
                cumulativeMap[day] = (cumulativeMap[day] ?: 0) + views
            }
        }

        val totalViews = cumulativeMap.values.maxOrNull() ?: 0
        val maxViews = totalViews.coerceAtLeast(1)

        val curve = milestones
            .filter { cumulativeMap.containsKey(it) }
            .map { day ->
                val views = cumulativeMap[day] ?: 0
                DataPoint(
                    day = day,
                    value = views,
                    normalizedPercent = Math.round(views.toDouble() / maxViews * 10000) / 100.0,
                )
            }

        return CohortGroupResponse(
            name = name,
            videoCount = videos.size,
            avgViews = if (videos.isNotEmpty()) totalViews / videos.size else 0,
            cumulativeViewCurve = curve,
        )
    }
}
