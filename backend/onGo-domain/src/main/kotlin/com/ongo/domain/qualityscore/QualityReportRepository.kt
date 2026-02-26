package com.ongo.domain.qualityscore

interface QualityReportRepository {
    fun findById(id: Long): QualityReport?
    fun findByUserId(userId: Long): List<QualityReport>
    fun save(report: QualityReport): QualityReport
    fun update(report: QualityReport): QualityReport
    fun delete(id: Long)
}
