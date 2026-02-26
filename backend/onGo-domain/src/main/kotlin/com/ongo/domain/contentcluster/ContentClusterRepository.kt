package com.ongo.domain.contentcluster

interface ContentClusterRepository {
    fun findByWorkspaceId(workspaceId: Long): List<ContentCluster>
    fun findById(id: Long): ContentCluster?
}
