package com.ongo.application.crossanalytics

import com.ongo.application.crossanalytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.crossanalytics.CrossPlatformReport
import com.ongo.domain.crossanalytics.CrossAnalyticsRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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
     * 기간별 크로스 플랫폼 분석 조회 - analytics_daily 기반 집계
     */
    fun getAnalytics(userId: Long, period: String?): CrossPlatformReportResponse {
        val effectivePeriod = period ?: "7d"
        val days = effectivePeriod.replace("d", "").toIntOrNull() ?: 7

        // 최근 리포트가 있으면 반환, 없으면 빈 리포트
        val existing = crossAnalyticsRepository.findByUserId(userId)
            .firstOrNull { it.period == effectivePeriod }

        if (existing != null) return existing.toResponse()

        // analytics_daily 기반으로 크로스 플랫폼 데이터 집계
        val rawData = analyticsRepository.findCrossPlatformMetrics(userId, days)

        val platformSummaries = rawData.groupBy { it.platform }.map { (platform, items) ->
            mapOf(
                "platform" to platform,
                "views" to items.sumOf { it.views },
                "likes" to items.sumOf { it.likes },
                "comments" to items.sumOf { it.comments },
                "shares" to items.sumOf { it.shares },
            )
        }

        return CrossPlatformReportResponse(
            id = 0L,
            period = effectivePeriod,
            contents = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                rawData.map { mapOf("videoId" to it.videoId, "title" to it.videoTitle, "platform" to it.platform) }
            ),
            platformSummaries = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(platformSummaries),
            audienceOverlap = "{}",
            recommendations = "[]",
            generatedAt = java.time.LocalDateTime.now(),
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
