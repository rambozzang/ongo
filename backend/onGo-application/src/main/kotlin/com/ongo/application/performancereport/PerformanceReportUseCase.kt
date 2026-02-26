package com.ongo.application.performancereport

import com.ongo.application.performancereport.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.performancereport.PerformanceReport
import com.ongo.domain.performancereport.PerformanceReportRepository
import com.ongo.domain.performancereport.ReportSection
import com.ongo.domain.performancereport.ReportSectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PerformanceReportUseCase(
    private val reportRepository: PerformanceReportRepository,
    private val sectionRepository: ReportSectionRepository,
) {

    fun getReports(userId: Long, status: String?): List<PerformanceReportResponse> {
        val reports = if (status != null) {
            reportRepository.findByStatus(userId, status)
        } else {
            reportRepository.findByUserId(userId)
        }
        return reports.map { it.toResponse() }
    }

    fun getSections(reportId: Long): List<ReportSectionResponse> {
        return sectionRepository.findByReportId(reportId).map { it.toResponse() }
    }

    @Transactional
    fun generate(userId: Long, request: GenerateReportRequest): PerformanceReportResponse {
        val report = PerformanceReport(
            userId = userId,
            title = request.title,
            startDate = request.startDate,
            endDate = request.endDate,
            status = "GENERATING",
        )
        return reportRepository.save(report).toResponse()
    }

    @Transactional
    fun deleteReport(userId: Long, reportId: Long) {
        reportRepository.findById(reportId)
            ?: throw NotFoundException("성과 보고서", reportId)
        reportRepository.deleteById(reportId)
    }

    fun getSummary(userId: Long): PerformanceReportSummaryResponse {
        val reports = reportRepository.findByUserId(userId)
        val scheduled = reports.count { it.status == "SCHEDULED" }
        return PerformanceReportSummaryResponse(
            totalReports = reports.size,
            scheduledReports = scheduled,
            avgViewsPerPeriod = if (reports.isNotEmpty()) reports.sumOf { it.totalViews } / reports.size else 0,
            bestPeriod = "",
            growthRate = 0.0,
        )
    }

    private fun PerformanceReport.toResponse() = PerformanceReportResponse(
        id = id!!,
        title = title,
        period = period,
        startDate = startDate,
        endDate = endDate,
        status = status,
        totalViews = totalViews,
        totalEngagement = totalEngagement,
        topVideoId = topVideoId,
        topVideoTitle = topVideoTitle,
        reportUrl = reportUrl,
        createdAt = createdAt,
    )

    private fun ReportSection.toResponse() = ReportSectionResponse(
        id = id!!,
        reportId = reportId,
        sectionType = sectionType,
        title = title,
        content = content,
        chartData = chartData,
        sortOrder = sortOrder,
    )
}
