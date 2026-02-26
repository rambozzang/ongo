package com.ongo.application.contentcluster.dto

data class ContentClusterResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val contentCount: Int,
    val avgViews: Long,
    val avgEngagement: Double,
    val topContent: String?,
    val tags: List<String>,
    val platform: String,
    val createdAt: String,
)

data class ClusterContentResponse(
    val id: Long,
    val title: String,
    val platform: String,
    val views: Long,
    val likes: Int,
    val engagement: Double,
    val publishedAt: String?,
    val similarity: Double,
)

data class ContentClusterSummaryResponse(
    val totalClusters: Int,
    val totalContents: Int,
    val avgClusterSize: Double,
    val bestCluster: String,
    val mostCommonTag: String,
)
