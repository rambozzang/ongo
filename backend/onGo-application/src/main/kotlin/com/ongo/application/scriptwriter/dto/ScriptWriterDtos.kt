package com.ongo.application.scriptwriter.dto

data class ScriptResponse(
    val id: Long,
    val title: String,
    val topic: String,
    val format: String,
    val tone: String,
    val status: String,
    val targetDuration: Int,
    val estimatedWordCount: Int,
    val content: List<ScriptSectionResponse>,
    val keywords: List<String>,
    val targetAudience: String?,
    val notes: String?,
    val creditCost: Int,
    val createdAt: String,
    val updatedAt: String,
)

data class ScriptSectionResponse(
    val id: Long,
    val type: String,
    val title: String,
    val content: String,
    val duration: Int,
    val notes: String?,
    val orderNumber: Int,
)

data class GenerateScriptRequest(
    val topic: String,
    val format: String = "LONG_FORM",
    val tone: String = "CASUAL",
    val targetDuration: Int = 600,
    val keywords: List<String>? = null,
    val targetAudience: String? = null,
    val additionalNotes: String? = null,
)

data class UpdateScriptRequest(
    val title: String? = null,
    val status: String? = null,
    val notes: String? = null,
)

data class ScriptSummaryResponse(
    val totalScripts: Int,
    val drafts: Int,
    val finals: Int,
    val totalCreditsUsed: Int,
    val avgDuration: Int,
    val favoriteFormat: String,
)

data class ScriptTemplateResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val format: String,
    val tone: String,
    val sections: String,
    val usageCount: Int,
)
