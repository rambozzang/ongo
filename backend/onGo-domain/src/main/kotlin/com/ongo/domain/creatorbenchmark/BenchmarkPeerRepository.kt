package com.ongo.domain.creatorbenchmark

interface BenchmarkPeerRepository {
    fun findByWorkspaceId(workspaceId: Long): List<BenchmarkPeer>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<BenchmarkPeer>
}
