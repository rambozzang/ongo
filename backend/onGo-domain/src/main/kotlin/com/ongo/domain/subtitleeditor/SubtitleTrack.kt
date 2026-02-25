package com.ongo.domain.subtitleeditor

import java.math.BigDecimal
import java.time.LocalDateTime

data class SubtitleTrack(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String? = null,
    val language: String,
    val status: String = "DRAFT",
    val cues: String = "[]", // JSONB as String
    val totalDuration: BigDecimal = BigDecimal.ZERO,
    val wordCount: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
