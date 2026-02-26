package com.ongo.application.contentcluster

import com.ongo.application.contentcluster.dto.*
import com.ongo.domain.contentcluster.*
import org.springframework.stereotype.Service

@Service
class ContentClusterUseCase(
    private val clusterRepository: ContentClusterRepository,
    private val contentRepository: ClusterContentRepository,
) {

    fun getClusters(workspaceId: Long): List<ContentClusterResponse> {
        return clusterRepository.findByWorkspaceId(workspaceId).map { toClusterResponse(it) }
    }

    fun getCluster(id: Long): ContentClusterResponse? {
        return clusterRepository.findById(id)?.let { toClusterResponse(it) }
    }

    fun getContents(clusterId: Long): List<ClusterContentResponse> {
        return contentRepository.findByClusterId(clusterId).map { toContentResponse(it) }
    }

    fun getSummary(workspaceId: Long): ContentClusterSummaryResponse {
        val clusters = clusterRepository.findByWorkspaceId(workspaceId)
        val totalContents = clusters.sumOf { it.contentCount }
        val avg = if (clusters.isNotEmpty()) totalContents.toDouble() / clusters.size else 0.0
        val best = clusters.maxByOrNull { it.avgEngagement }?.name ?: "-"
        val allTags = clusters.flatMap { it.tags }
        val topTag = allTags.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "-"
        return ContentClusterSummaryResponse(clusters.size, totalContents, avg, best, topTag)
    }

    private fun toClusterResponse(c: ContentCluster) = ContentClusterResponse(
        id = c.id, name = c.name, description = c.description,
        contentCount = c.contentCount, avgViews = c.avgViews,
        avgEngagement = c.avgEngagement.toDouble(), topContent = c.topContent,
        tags = c.tags, platform = c.platform, createdAt = c.createdAt.toString(),
    )

    private fun toContentResponse(c: ClusterContent) = ClusterContentResponse(
        id = c.id, title = c.title, platform = c.platform,
        views = c.views, likes = c.likes, engagement = c.engagement.toDouble(),
        publishedAt = c.publishedAt?.toString(), similarity = c.similarity.toDouble(),
    )
}
