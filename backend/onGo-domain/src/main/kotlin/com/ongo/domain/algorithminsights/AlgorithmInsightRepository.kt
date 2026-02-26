package com.ongo.domain.algorithminsights

interface AlgorithmInsightRepository {
    fun findByWorkspaceId(workspaceId: Long): List<AlgorithmInsight>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<AlgorithmInsight>
    fun findById(id: Long): AlgorithmInsight?
    fun save(insight: AlgorithmInsight): AlgorithmInsight
}
