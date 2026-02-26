package com.ongo.domain.subtitletranslation

import java.time.LocalDateTime

data class SupportedLanguage(
    val id: Long = 0,
    val code: String,
    val name: String,
    val nativeName: String,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
