package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.qualityscore.*
import org.springframework.stereotype.Repository

@Repository
class QualityReportStubRepository : QualityReportRepository {
    override fun findById(id: Long): QualityReport? = null
    override fun findByUserId(userId: Long): List<QualityReport> = emptyList()
    override fun save(report: QualityReport): QualityReport = report.copy(id = 1)
    override fun update(report: QualityReport): QualityReport = report
    override fun delete(id: Long) {}
}
