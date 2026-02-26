package com.ongo.domain.videoseo

import java.time.LocalDateTime

data class SeoAnalysis(
    val id: Long = 0,
    val workspaceId: Long,
    val videoTitle: String,
    val platform: String,
    val overallScore: Int = 0,
    val titleScore: Int = 0,
    val descriptionScore: Int = 0,
    val tagScore: Int = 0,
    val thumbnailScore: Int = 0,
    val suggestions: List<String> = emptyList(),
    val analyzedAt: LocalDateTime = LocalDateTime.now(),
)
