package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatorbenchmark.*
import org.springframework.stereotype.Repository

@Repository
class BenchmarkPeerStubRepository : BenchmarkPeerRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<BenchmarkPeer> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<BenchmarkPeer> = emptyList()
}

@Repository
class BenchmarkResultStubRepository : BenchmarkResultRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<BenchmarkResult> = emptyList()
    override fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<BenchmarkResult> = emptyList()
}
