package com.ongo.application.subtitletranslation.dto

data class SubtitleTranslationResponse(
    val id: Long,
    val videoTitle: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val status: String,
    val progress: Int,
    val sourceLineCount: Int,
    val translatedLineCount: Int,
    val quality: Int,
    val cost: Int,
    val createdAt: String,
    val completedAt: String?,
)

data class TranslationLineResponse(
    val id: Long,
    val translationId: Long,
    val lineNumber: Int,
    val startTime: String,
    val endTime: String,
    val sourceText: String,
    val translatedText: String,
    val isEdited: Boolean,
)

data class SupportedLanguageResponse(
    val code: String,
    val name: String,
    val nativeName: String,
)

data class CreateTranslationRequest(
    val videoId: Long,
    val videoTitle: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)

data class UpdateTranslationLineRequest(
    val translatedText: String,
)

data class SubtitleTranslationSummaryResponse(
    val totalTranslations: Int,
    val completedCount: Int,
    val inProgressCount: Int,
    val totalCreditsUsed: Int,
    val avgQuality: Double,
)
