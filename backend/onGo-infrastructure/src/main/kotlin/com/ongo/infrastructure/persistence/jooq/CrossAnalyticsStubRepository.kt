package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.crossanalytics.*
import org.springframework.stereotype.Repository

@Repository
class CrossAnalyticsStubRepository : CrossAnalyticsRepository {
    override fun findById(id: Long): CrossPlatformReport? = null
    override fun findByUserId(userId: Long): List<CrossPlatformReport> = emptyList()
    override fun save(report: CrossPlatformReport): CrossPlatformReport = report.copy(id = 1)
    override fun update(report: CrossPlatformReport): CrossPlatformReport = report
    override fun delete(id: Long) {}
}
