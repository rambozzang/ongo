package com.ongo.domain.channelhealth

interface HealthTrendRepository {
    fun findByMetricId(metricId: Long): List<HealthTrend>
    fun save(trend: HealthTrend): HealthTrend
}
