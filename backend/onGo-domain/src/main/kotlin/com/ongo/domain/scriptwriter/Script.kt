package com.ongo.domain.scriptwriter

import java.time.LocalDateTime

data class Script(
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val topic: String,
    val format: String = "LONG_FORM",
    val tone: String = "CASUAL",
    val status: String = "DRAFT",
    val targetDuration: Int = 600,
    val estimatedWordCount: Int = 0,
    val keywords: List<String> = emptyList(),
    val targetAudience: String? = null,
    val notes: String? = null,
    val creditCost: Int = 3,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
