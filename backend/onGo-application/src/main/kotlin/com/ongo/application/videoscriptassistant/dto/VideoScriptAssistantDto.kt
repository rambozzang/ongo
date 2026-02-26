package com.ongo.application.videoscriptassistant.dto

import java.time.LocalDateTime

data class VideoScriptResponse(
    val id: Long,
    val title: String,
    val videoId: Long?,
    val videoTitle: String?,
    val content: String,
    val tone: String,
    val targetLength: Int,
    val wordCount: Int,
    val hookLine: String?,
    val ctaText: String?,
    val status: String,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
)

data class ScriptSuggestionResponse(
    val id: Long,
    val scriptId: Long,
    val sectionType: String,
    val originalText: String,
    val suggestedText: String,
    val reason: String,
    val isApplied: Boolean,
    val createdAt: LocalDateTime?,
)

data class VideoScriptAssistantSummaryResponse(
    val totalScripts: Int,
    val completedScripts: Int,
    val avgWordCount: Int,
    val topTone: String,
    val suggestionsApplied: Int,
)

data class GenerateScriptRequest(
    val title: String,
    val tone: String,
    val targetLength: Int,
)
