package com.ongo.application.hashtagstrategy.dto

import java.time.LocalDateTime

data class HashtagSetResponse(
    val id: Long,
    val name: String,
    val hashtags: String,
    val totalReachEstimate: Long,
    val overallDifficulty: String?,
    val score: Int,
    val platform: String?,
    val createdAt: LocalDateTime?,
)

data class CreateHashtagSetRequest(
    val name: String,
    val hashtags: String = "[]",
    val totalReachEstimate: Long = 0,
    val overallDifficulty: String? = null,
    val score: Int = 0,
    val platform: String? = null,
)
