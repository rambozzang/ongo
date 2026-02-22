package com.ongo.application.translation.dto

data class TranslationResponse(
    val id: Long,
    val videoId: Long,
    val language: String,
    val title: String?,
    val description: String?,
    val tags: List<String>,
    val subtitleContent: String?,
    val status: String,
    val createdAt: String?,
)

data class TranslateRequest(
    val languages: List<String>,
)

data class UpdateTranslationRequest(
    val title: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val subtitleContent: String? = null,
)
