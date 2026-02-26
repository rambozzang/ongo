package com.ongo.domain.videoscriptassistant

import java.time.LocalDateTime

data class ScriptSuggestion(
    val id: Long? = null,
    val scriptId: Long,
    val sectionType: String,
    val originalText: String,
    val suggestedText: String,
    val reason: String,
    val isApplied: Boolean = false,
    val createdAt: LocalDateTime? = null,
)
