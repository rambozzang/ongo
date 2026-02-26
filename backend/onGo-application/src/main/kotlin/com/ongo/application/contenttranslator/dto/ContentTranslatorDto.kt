package com.ongo.application.contenttranslator.dto

import java.time.LocalDateTime

data class TranslationJobResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val contentType: String,
    val originalText: String,
    val translatedText: String?,
    val quality: Int?,
    val status: String,
    val createdAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
)

data class TranslationGlossaryResponse(
    val id: Long,
    val sourceWord: String,
    val targetWord: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val context: String?,
    val createdAt: LocalDateTime?,
)

data class ContentTranslatorSummaryResponse(
    val totalTranslations: Int,
    val completedTranslations: Int,
    val avgQuality: Int,
    val topLanguagePair: String,
    val glossarySize: Int,
)

data class TranslateRequest(
    val videoId: Long,
    val targetLanguage: String,
    val contentType: String,
)

data class AddGlossaryTermRequest(
    val sourceWord: String,
    val targetWord: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)
