package com.ongo.domain.performancereport

interface ReportSectionRepository {
    fun findByReportId(reportId: Long): List<ReportSection>
    fun save(section: ReportSection): ReportSection
}
