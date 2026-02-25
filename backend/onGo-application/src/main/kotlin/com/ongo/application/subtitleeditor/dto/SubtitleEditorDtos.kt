package com.ongo.application.subtitleeditor.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class SubtitleTrackResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String?,
    val language: String,
    val status: String,
    val cues: String,
    val totalDuration: BigDecimal,
    val wordCount: Int,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class CreateSubtitleTrackRequest(
    val videoId: Long,
    val videoTitle: String? = null,
    val language: String,
    val cues: String = "[]",
    val totalDuration: BigDecimal = BigDecimal.ZERO,
    val wordCount: Int = 0,
)

data class UpdateSubtitleTrackRequest(
    val language: String? = null,
    val status: String? = null,
    val cues: String? = null,
    val totalDuration: BigDecimal? = null,
    val wordCount: Int? = null,
)
