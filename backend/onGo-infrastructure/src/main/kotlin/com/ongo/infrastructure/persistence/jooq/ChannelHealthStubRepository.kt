package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.channelhealth.*
import org.springframework.stereotype.Repository

@Repository
class ChannelHealthMetricStubRepository : ChannelHealthMetricRepository {
    override fun findById(id: Long): ChannelHealthMetric? = null
    override fun findByUserId(userId: Long): List<ChannelHealthMetric> = emptyList()
    override fun save(metric: ChannelHealthMetric): ChannelHealthMetric = metric.copy(id = 1)
    override fun deleteById(id: Long) {}
}

@Repository
class HealthTrendStubRepository : HealthTrendRepository {
    override fun findByMetricId(metricId: Long): List<HealthTrend> = emptyList()
    override fun save(trend: HealthTrend): HealthTrend = trend.copy(id = 1)
}
