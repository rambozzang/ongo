package com.ongo.domain.algorithminsights

interface AlgorithmChangeRepository {
    fun findByWorkspaceId(workspaceId: Long): List<AlgorithmChange>
    fun findById(id: Long): AlgorithmChange?
    fun save(change: AlgorithmChange): AlgorithmChange
}
