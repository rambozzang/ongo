package com.ongo.application.metarewrite.dto

import java.time.LocalDateTime

data class MetaRewriteResponse(
    val id: Long,
    val videoId: Long,
    val originalTitle: String,
    val originalDescription: String?,
    val suggestedTitle: String,
    val suggestedDescription: String,
    val suggestedTags: List<String>,
    val reasoning: String,
    val expectedImpactPercent: Int,
    val createdAt: LocalDateTime,
)
