package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.performancereport.*
import org.springframework.stereotype.Repository

@Repository
class PerformanceReportStubRepository : PerformanceReportRepository {
    override fun findById(id: Long): PerformanceReport? = null
    override fun findByUserId(userId: Long): List<PerformanceReport> = emptyList()
    override fun findByStatus(userId: Long, status: String): List<PerformanceReport> = emptyList()
    override fun save(report: PerformanceReport): PerformanceReport = report.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
    override fun deleteById(id: Long) {}
}

@Repository
class ReportSectionStubRepository : ReportSectionRepository {
    override fun findByReportId(reportId: Long): List<ReportSection> = emptyList()
    override fun save(section: ReportSection): ReportSection = section.copy(id = 1)
}
