package com.ongo.domain.scriptwriter

import java.time.LocalDateTime

data class ScriptSection(
    val id: Long = 0,
    val scriptId: Long,
    val type: String,
    val title: String,
    val content: String = "",
    val duration: Int = 0,
    val notes: String? = null,
    val orderNumber: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
