package com.ongo.application.contentrewriter.dto

import java.time.LocalDateTime

data class RewriteRequest(
    val sourceText: String,
    val sourceType: String = "GENERAL",
    val targetFormats: List<String> = listOf("YOUTUBE", "TIKTOK", "INSTAGRAM"),
)

data class RewriteResponse(
    val id: Long,
    val sourceText: String,
    val sourceType: String,
    val targetFormats: String,
    val results: String,
    val createdAt: LocalDateTime?,
)

data class RewriteHistoryResponse(
    val id: Long,
    val sourceText: String,
    val sourceType: String,
    val targetFormats: String,
    val results: String,
    val createdAt: LocalDateTime?,
)
