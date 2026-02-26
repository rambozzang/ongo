package com.ongo.domain.performancereport

interface PerformanceReportRepository {
    fun findById(id: Long): PerformanceReport?
    fun findByUserId(userId: Long): List<PerformanceReport>
    fun findByStatus(userId: Long, status: String): List<PerformanceReport>
    fun save(report: PerformanceReport): PerformanceReport
    fun updateStatus(id: Long, status: String)
    fun deleteById(id: Long)
}
