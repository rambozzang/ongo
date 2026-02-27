package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentcluster.*
import org.springframework.stereotype.Repository

@Repository
class ContentClusterStubRepository : ContentClusterRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<ContentCluster> = emptyList()
    override fun findById(id: Long): ContentCluster? = null
}

@Repository
class ClusterContentStubRepository : ClusterContentRepository {
    override fun findByClusterId(clusterId: Long): List<ClusterContent> = emptyList()
}
