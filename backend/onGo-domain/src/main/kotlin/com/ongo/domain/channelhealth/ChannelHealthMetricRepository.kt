package com.ongo.domain.channelhealth

interface ChannelHealthMetricRepository {
    fun findById(id: Long): ChannelHealthMetric?
    fun findByUserId(userId: Long): List<ChannelHealthMetric>
    fun save(metric: ChannelHealthMetric): ChannelHealthMetric
    fun deleteById(id: Long)
}
