package com.ongo.domain.creatorbenchmark

interface BenchmarkResultRepository {
    fun findByWorkspaceId(workspaceId: Long): List<BenchmarkResult>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<BenchmarkResult>
}
