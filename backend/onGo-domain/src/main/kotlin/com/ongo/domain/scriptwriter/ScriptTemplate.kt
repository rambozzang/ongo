package com.ongo.domain.scriptwriter

import java.time.LocalDateTime

data class ScriptTemplate(
    val id: Long = 0,
    val userId: Long? = null,
    val name: String,
    val description: String? = null,
    val format: String,
    val tone: String,
    val sections: String = "[]",
    val usageCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
