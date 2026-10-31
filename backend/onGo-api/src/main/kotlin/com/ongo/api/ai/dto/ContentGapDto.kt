package com.ongo.api.ai.dto

data class ContentGapRequest(
    val channelIds: List<Long>? = null,
    val includeCompetitors: Boolean = true,
)
