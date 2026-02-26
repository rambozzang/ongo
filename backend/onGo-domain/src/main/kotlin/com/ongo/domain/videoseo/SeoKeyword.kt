package com.ongo.domain.videoseo

import java.time.LocalDateTime

data class SeoKeyword(
    val id: Long = 0,
    val analysisId: Long,
    val keyword: String,
    val searchVolume: Long = 0,
    val competition: String = "MEDIUM",
    val relevance: Int = 0,
    val trend: String = "STABLE",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
