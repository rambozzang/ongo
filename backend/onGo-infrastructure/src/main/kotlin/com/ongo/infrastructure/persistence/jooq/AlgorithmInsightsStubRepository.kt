package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.algorithminsights.*
import org.springframework.stereotype.Repository

@Repository
class AlgorithmChangeStubRepository : AlgorithmChangeRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<AlgorithmChange> = emptyList()
    override fun findById(id: Long): AlgorithmChange? = null
    override fun save(change: AlgorithmChange): AlgorithmChange = change.copy(id = 1)
}

@Repository
class AlgorithmInsightStubRepository : AlgorithmInsightRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<AlgorithmInsight> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<AlgorithmInsight> = emptyList()
    override fun findById(id: Long): AlgorithmInsight? = null
    override fun save(insight: AlgorithmInsight): AlgorithmInsight = insight.copy(id = 1)
}

@Repository
class AlgorithmScoreStubRepository : AlgorithmScoreRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<AlgorithmScore> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): AlgorithmScore? = null
    override fun save(score: AlgorithmScore): AlgorithmScore = score.copy(id = 1)
    override fun update(score: AlgorithmScore): AlgorithmScore = score
}
