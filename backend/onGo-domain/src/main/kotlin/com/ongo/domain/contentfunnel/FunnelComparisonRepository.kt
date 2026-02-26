package com.ongo.domain.contentfunnel

interface FunnelComparisonRepository {
    fun findById(id: Long): FunnelComparison?
    fun findByUserId(userId: Long): List<FunnelComparison>
    fun save(comparison: FunnelComparison): FunnelComparison
}
