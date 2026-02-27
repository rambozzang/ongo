package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentfunnel.*
import org.springframework.stereotype.Repository

@Repository
class FunnelStageStubRepository : FunnelStageRepository {
    override fun findByVideoId(videoId: Long): List<FunnelStage> = emptyList()
    override fun findByUserId(userId: Long): List<FunnelStage> = emptyList()
    override fun save(stage: FunnelStage): FunnelStage = stage.copy(id = 1)
}

@Repository
class FunnelComparisonStubRepository : FunnelComparisonRepository {
    override fun findById(id: Long): FunnelComparison? = null
    override fun findByUserId(userId: Long): List<FunnelComparison> = emptyList()
    override fun save(comparison: FunnelComparison): FunnelComparison = comparison.copy(id = 1)
}
