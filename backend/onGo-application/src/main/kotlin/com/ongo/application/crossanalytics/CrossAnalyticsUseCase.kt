package com.ongo.application.crossanalytics

import com.ongo.application.crossanalytics.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.crossanalytics.CrossPlatformReport
import com.ongo.domain.crossanalytics.CrossAnalyticsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CrossAnalyticsUseCase(
    private val crossAnalyticsRepository: CrossAnalyticsRepository,
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
