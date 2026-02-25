package com.ongo.domain.crossanalytics

interface CrossAnalyticsRepository {
    fun findById(id: Long): CrossPlatformReport?
    fun findByUserId(userId: Long): List<CrossPlatformReport>
    fun save(report: CrossPlatformReport): CrossPlatformReport
    fun update(report: CrossPlatformReport): CrossPlatformReport
    fun delete(id: Long)
}
