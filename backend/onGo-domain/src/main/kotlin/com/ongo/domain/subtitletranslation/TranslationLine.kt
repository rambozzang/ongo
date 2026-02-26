package com.ongo.domain.subtitletranslation

import java.time.LocalDateTime

data class TranslationLine(
    val id: Long = 0,
    val translationId: Long,
    val lineNumber: Int,
    val startTime: String,
    val endTime: String,
    val sourceText: String,
    val translatedText: String = "",
    val isEdited: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
