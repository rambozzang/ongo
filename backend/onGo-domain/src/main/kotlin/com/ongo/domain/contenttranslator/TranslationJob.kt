package com.ongo.domain.contenttranslator

import java.time.LocalDateTime

data class TranslationJob(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val contentType: String = "ALL",
    val originalText: String,
    val translatedText: String? = null,
    val quality: Int? = null,
    val status: String = "PENDING",
    val createdAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)
