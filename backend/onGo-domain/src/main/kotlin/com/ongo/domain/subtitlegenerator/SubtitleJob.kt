package com.ongo.domain.subtitlegenerator

import java.time.Instant

data class SubtitleJob(
    val id: Long = 0,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val platform: String,
    val language: String = "ko",
    val status: String = "PENDING",
    val progress: Int = 0,
    val subtitleUrl: String? = null,
    val wordCount: Int = 0,
    val duration: Int = 0,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
)
