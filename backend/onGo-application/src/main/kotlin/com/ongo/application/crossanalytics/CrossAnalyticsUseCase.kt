package com.ongo.application.crossanalytics

import org.springframework.context.annotation.Profile
import com.ongo.application.crossanalytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformDetailRaw
import com.ongo.domain.crossanalytics.CrossPlatformReport
import com.ongo.domain.crossanalytics.CrossAnalyticsRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Profile("wip")
@Service
class CrossAnalyticsUseCase(
    private val crossAnalyticsRepository: CrossAnalyticsRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {

    fun listReports(userId: Long): List<CrossPlatformReportResponse> {
        return crossAnalyticsRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getReport(userId: Long, reportId: Long): CrossPlatformReportResponse {
        val report = crossAnalyticsRepository.findById(reportId) ?: throw NotFoundException("크로스 플랫폼 리포트", reportId)
        if (report.userId != userId) throw ForbiddenException("해당 크로스 플랫폼 리포트에 대한 권한이 없습니다")
        return report.toResponse()
    }

    @Transactional
    fun generateReport(userId: Long, request: GenerateReportRequest): CrossPlatformReportResponse {
        val report = CrossPlatformReport(
            userId = userId,
            period = request.period,
            contents = request.contents,
            platformSummaries = request.platformSummaries,
            audienceOverlap = request.audienceOverlap,
            recommendations = request.recommendations,
        )
        return crossAnalyticsRepository.save(report).toResponse()
    }

    /**
     * 기간별 크로스 플랫폼 분석 조회 - analytics_daily 기반 실데이터 집계.
     * 프론트엔드 CrossPlatformReport 타입에 맞춰 구조화된 응답을 반환한다.
     */
    fun getAnalytics(userId: Long, period: String?): CrossAnalyticsResponse {
        val effectivePeriod = period ?: "7d"
        val days = effectivePeriod.replace("d", "").toIntOrNull() ?: 7

        // analytics_daily + videos + video_uploads JOIN 집계
        val rawData = analyticsRepository.findCrossPlatformDetailMetrics(userId, days)

        if (rawData.isEmpty()) {
            return CrossAnalyticsResponse(
                id = 0L,
                period = effectivePeriod,
                contents = emptyList(),
                platformSummaries = emptyList(),
                audienceOverlap = emptyList(),
                recommendations = emptyList(),
                generatedAt = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toString(),
            )
        }

        // 1. 영상별 크로스 플랫폼 콘텐츠 구성
        val byVideo = rawData.groupBy { it.videoId }
        val contents = byVideo.map { (videoId, items) ->
            val first = items.first()
            val platforms = items.map { raw ->
                val impressions = raw.impressions.coerceAtLeast(1)
                val ctr = if (impressions > 0) {
                    Math.round(raw.views.toDouble() / impressions * 100 * 100) / 100.0
                } else 0.0

                ContentPlatformMetrics(
                    platform = raw.platform.lowercase(),
                    videoId = raw.videoUploadId.toString(),
                    views = raw.views,
                    likes = raw.likes,
                    comments = raw.comments,
                    shares = raw.shares,
                    avgWatchTime = raw.avgViewDurationSeconds,
                    ctr = ctr,
                    publishedAt = raw.publishedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                )
            }

            CrossContentItem(
                id = videoId,
                title = first.videoTitle ?: "",
                thumbnailUrl = first.thumbnailUrls.firstOrNull() ?: "",
                publishedAt = first.publishedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toString(),
                platforms = platforms,
            )
        }

        // 2. 플랫폼별 성과 요약
        val byPlatform = rawData.groupBy { it.platform.lowercase() }
        val platformSummaries = byPlatform.map { (platform, items) ->
            val totalViews = items.sumOf { it.views }
            val totalLikes = items.sumOf { it.likes }
            val totalComments = items.sumOf { it.comments }
            val totalShares = items.sumOf { it.shares }
            val totalImpressions = items.sumOf { it.impressions }

            val avgEngagement = if (totalViews > 0) {
                Math.round(((totalLikes + totalComments + totalShares).toDouble() / totalViews) * 100 * 100) / 100.0
            } else 0.0

            val avgCtr = if (totalImpressions > 0) {
                Math.round(totalViews.toDouble() / totalImpressions * 100 * 100) / 100.0
            } else 0.0

            val bestContentId = items.maxByOrNull { it.views }?.videoId ?: 0L

            PlatformPerformanceSummary(
                platform = platform,
                totalViews = totalViews,
                totalLikes = totalLikes,
                avgEngagement = avgEngagement,
                avgCtr = avgCtr,
                contentCount = items.map { it.videoId }.distinct().size,
                bestPerformingContentId = bestContentId,
            )
        }

        // 3. 오디언스 중복 추정 (플랫폼 쌍별로 공통 콘텐츠 비율 기반)
        val audienceOverlap = estimateAudienceOverlap(byVideo, byPlatform.keys.toList())

        // 4. 추천 생성
        val recommendations = generateRecommendations(platformSummaries, contents)

        return CrossAnalyticsResponse(
            id = 0L,
            period = effectivePeriod,
            contents = contents,
            platformSummaries = platformSummaries,
            audienceOverlap = audienceOverlap,
            recommendations = recommendations,
            generatedAt = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toString(),
        )
    }

    /**
     * 콘텐츠 간 비교 분석
     */
    fun compareContent(userId: Long, request: ContentCompareRequest): ContentCompareResponse {
        if (request.videoIds.isEmpty()) return ContentCompareResponse(contents = emptyList())

        val days = request.period.replace("d", "").toIntOrNull() ?: 30
        val from = LocalDate.now().minusDays(days.toLong())
        val to = LocalDate.now()

        val videos = videoRepository.findByIds(request.videoIds).associateBy { it.id!! }

        // 소유권 확인
        videos.values.forEach { video ->
            if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        val uploadsByVideoId = videoUploadRepository.findByVideoIds(request.videoIds)
        val allUploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }
        val analyticsByUploadId = analyticsRepository.findByVideoUploadIdsAndDateRange(allUploadIds, from, to)

        val contents = request.videoIds.flatMap { videoId ->
            val video = videos[videoId] ?: return@flatMap emptyList()
            val uploads = uploadsByVideoId[videoId] ?: return@flatMap emptyList()

            uploads.map { upload ->
                val analytics = analyticsByUploadId[upload.id!!] ?: emptyList()
                CrossPlatformContent(
                    videoId = videoId,
                    title = video.title ?: "",
                    platform = upload.platform.name,
                    views = analytics.sumOf { it.views.toLong() },
                    likes = analytics.sumOf { it.likes.toLong() },
                    comments = analytics.sumOf { it.commentsCount.toLong() },
                )
            }
        }

        return ContentCompareResponse(contents = contents)
    }

    /**
     * 리포트 CSV 내보내기
     */
    fun exportReport(userId: Long, reportId: Long, format: String): ByteArray {
        val report = crossAnalyticsRepository.findById(reportId)
            ?: throw NotFoundException("크로스 플랫폼 리포트", reportId)
        if (report.userId != userId) throw ForbiddenException("해당 크로스 플랫폼 리포트에 대한 권한이 없습니다")

        return when (format.lowercase()) {
            "csv" -> generateCsv(report)
            else -> generateCsv(report) // 기본은 CSV
        }
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 오디언스 중복 추정: 같은 영상이 여러 플랫폼에 업로드된 비율로 중복 추정
     */
    private fun estimateAudienceOverlap(
        byVideo: Map<Long, List<CrossPlatformDetailRaw>>,
        platforms: List<String>,
    ): List<AudienceOverlapItem> {
        if (platforms.size < 2) return emptyList()

        val overlaps = mutableListOf<AudienceOverlapItem>()
        val totalVideos = byVideo.size.coerceAtLeast(1)

        for (i in platforms.indices) {
            for (j in i + 1 until platforms.size) {
                val platformA = platforms[i]
                val platformB = platforms[j]

                // 두 플랫폼 모두에 업로드된 영상 수
                val sharedCount = byVideo.count { (_, items) ->
                    val platformsInVideo = items.map { it.platform.lowercase() }.toSet()
                    platformA in platformsInVideo && platformB in platformsInVideo
                }

                val overlapPercent = (sharedCount.toDouble() / totalVideos * 100).toInt().coerceIn(0, 100)

                overlaps.add(
                    AudienceOverlapItem(
                        platforms = listOf(platformA, platformB),
                        overlapPercent = overlapPercent,
                    )
                )
            }
        }

        return overlaps
    }

    /**
     * 분석 데이터 기반 추천 생성
     */
    private fun generateRecommendations(
        summaries: List<PlatformPerformanceSummary>,
        contents: List<CrossContentItem>,
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (summaries.isEmpty()) return recommendations

        // 가장 높은 조회수 플랫폼
        val bestViewsPlatform = summaries.maxByOrNull { it.totalViews }
        if (bestViewsPlatform != null) {
            val platformLabel = platformDisplayName(bestViewsPlatform.platform)
            recommendations.add("${platformLabel}에서 가장 높은 도달률을 보이고 있습니다. 이 플랫폼에 맞는 콘텐츠를 강화하세요.")
        }

        // 가장 높은 참여율 플랫폼
        val bestEngagementPlatform = summaries.maxByOrNull { it.avgEngagement }
        if (bestEngagementPlatform != null && bestEngagementPlatform.platform != bestViewsPlatform?.platform) {
            val platformLabel = platformDisplayName(bestEngagementPlatform.platform)
            recommendations.add("${platformLabel}의 참여율이 ${bestEngagementPlatform.avgEngagement}%로 가장 높습니다. 커뮤니티 활성화를 고려하세요.")
        }

        // CTR이 낮은 플랫폼
        val lowCtrPlatform = summaries.filter { it.avgCtr > 0 }.minByOrNull { it.avgCtr }
        if (lowCtrPlatform != null && lowCtrPlatform.avgCtr < 5.0) {
            val platformLabel = platformDisplayName(lowCtrPlatform.platform)
            recommendations.add("${platformLabel}의 CTR이 ${lowCtrPlatform.avgCtr}%로 낮습니다. 썸네일 최적화를 시도해보세요.")
        }

        // 모든 플랫폼에서 우수한 콘텐츠
        val multiPlatformContents = contents.filter { it.platforms.size >= 2 }
        if (multiPlatformContents.isNotEmpty()) {
            val bestContent = multiPlatformContents.maxByOrNull { content ->
                content.platforms.sumOf { it.views }
            }
            if (bestContent != null) {
                recommendations.add("\"${bestContent.title}\" 콘텐츠가 여러 플랫폼에서 우수한 성과를 보이고 있습니다.")
            }
        }

        return recommendations
    }

    private fun platformDisplayName(platform: String): String = when (platform.lowercase()) {
        "youtube" -> "YouTube"
        "tiktok" -> "TikTok"
        "instagram" -> "Instagram"
        "naverclip", "naver_clip" -> "Naver Clip"
        else -> platform
    }

    private fun generateCsv(report: CrossPlatformReport): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("Report ID,Period,Generated At")
        sb.appendLine("${report.id},${report.period},${report.generatedAt}")
        sb.appendLine()
        sb.appendLine("Platform Summaries")
        sb.appendLine(report.platformSummaries)
        sb.appendLine()
        sb.appendLine("Contents")
        sb.appendLine(report.contents)
        sb.appendLine()
        sb.appendLine("Recommendations")
        sb.appendLine(report.recommendations)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun CrossPlatformReport.toResponse() = CrossPlatformReportResponse(
        id = id!!,
        period = period,
        contents = contents,
        platformSummaries = platformSummaries,
        audienceOverlap = audienceOverlap,
        recommendations = recommendations,
        generatedAt = generatedAt,
    )
}
