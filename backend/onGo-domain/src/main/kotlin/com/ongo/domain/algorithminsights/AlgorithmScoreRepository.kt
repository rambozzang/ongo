package com.ongo.domain.algorithminsights

interface AlgorithmScoreRepository {
    fun findByWorkspaceId(workspaceId: Long): List<AlgorithmScore>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): AlgorithmScore?
    fun save(score: AlgorithmScore): AlgorithmScore
    fun update(score: AlgorithmScore): AlgorithmScore
}
