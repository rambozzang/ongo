package com.ongo.domain.contenttranslator

import java.time.LocalDateTime

data class TranslationGlossary(
    val id: Long? = null,
    val userId: Long,
    val sourceWord: String,
    val targetWord: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val context: String? = null,
    val createdAt: LocalDateTime? = null,
)
