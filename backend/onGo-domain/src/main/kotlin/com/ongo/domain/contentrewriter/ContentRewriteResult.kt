package com.ongo.domain.contentrewriter

import java.time.LocalDateTime

data class ContentRewriteResult(
    val id: Long? = null,
    val userId: Long,
    val sourceText: String,
    val sourceType: String = "GENERAL",
    val targetFormats: String = "[]", // JSONB as String
    val results: String = "[]", // JSONB as String
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
