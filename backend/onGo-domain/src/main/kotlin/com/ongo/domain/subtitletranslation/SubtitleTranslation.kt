package com.ongo.domain.subtitletranslation

import java.time.LocalDateTime

data class SubtitleTranslation(
    val id: Long = 0,
    val workspaceId: Long,
    val videoTitle: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val status: String = "PENDING",
    val progress: Int = 0,
    val sourceLineCount: Int = 0,
    val translatedLineCount: Int = 0,
    val quality: Int = 0,
    val cost: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val completedAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
