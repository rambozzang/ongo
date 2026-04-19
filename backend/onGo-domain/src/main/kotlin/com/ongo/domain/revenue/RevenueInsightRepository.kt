package com.ongo.domain.revenue

interface RevenueInsightRepository {
    fun findByUserId(userId: Long, page: Int, size: Int): List<RevenueInsight>
    fun countByUserId(userId: Long): Long
    fun save(insight: RevenueInsight): RevenueInsight
}
