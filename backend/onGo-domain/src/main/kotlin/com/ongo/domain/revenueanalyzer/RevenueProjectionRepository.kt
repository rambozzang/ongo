package com.ongo.domain.revenueanalyzer

interface RevenueProjectionRepository {
    fun findByChannelId(channelId: Long): List<RevenueProjection>
    fun findByUserId(userId: Long): List<RevenueProjection>
    fun save(projection: RevenueProjection): RevenueProjection
}
