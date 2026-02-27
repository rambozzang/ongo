package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.revenueanalyzer.*
import org.springframework.stereotype.Repository

@Repository
class RevenueProjectionStubRepository : RevenueProjectionRepository {
    override fun findByChannelId(channelId: Long): List<RevenueProjection> = emptyList()
    override fun findByUserId(userId: Long): List<RevenueProjection> = emptyList()
    override fun save(projection: RevenueProjection): RevenueProjection = projection.copy(id = 1)
}

@Repository
class RevenueStreamStubRepository : RevenueStreamRepository {
    override fun findById(id: Long): RevenueStream? = null
    override fun findByUserId(userId: Long): List<RevenueStream> = emptyList()
    override fun findBySource(userId: Long, source: String): List<RevenueStream> = emptyList()
    override fun save(stream: RevenueStream): RevenueStream = stream.copy(id = 1)
}
