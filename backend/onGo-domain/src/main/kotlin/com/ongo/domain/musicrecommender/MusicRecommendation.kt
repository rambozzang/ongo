package com.ongo.domain.musicrecommender

import java.time.LocalDateTime

data class MusicRecommendation(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val selectedTrackId: Long? = null,
    val status: String = "PENDING",
    val createdAt: LocalDateTime? = null,
)
