package com.ongo.domain.revenueanalyzer

interface RevenueStreamRepository {
    fun findById(id: Long): RevenueStream?
    fun findByUserId(userId: Long): List<RevenueStream>
    fun findBySource(userId: Long, source: String): List<RevenueStream>
    fun save(stream: RevenueStream): RevenueStream
}
