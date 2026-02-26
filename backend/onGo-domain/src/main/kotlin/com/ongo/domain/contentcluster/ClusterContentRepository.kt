package com.ongo.domain.contentcluster

interface ClusterContentRepository {
    fun findByClusterId(clusterId: Long): List<ClusterContent>
}
