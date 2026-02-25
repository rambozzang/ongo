package com.ongo.domain.hashtagstrategy

import java.time.LocalDateTime

data class HashtagSet(
    val id: Long? = null,
    val userId: Long,
    val name: String,
    val hashtags: String = "[]", // JSONB as String
    val totalReachEstimate: Long = 0,
    val overallDifficulty: String? = null,
    val score: Int = 0,
    val platform: String? = null,
    val createdAt: LocalDateTime? = null,
)
