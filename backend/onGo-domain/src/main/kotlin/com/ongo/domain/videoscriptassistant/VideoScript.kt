package com.ongo.domain.videoscriptassistant

import java.time.LocalDateTime

data class VideoScript(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val videoId: Long? = null,
    val videoTitle: String? = null,
    val content: String = "",
    val tone: String = "CASUAL",
    val targetLength: Int = 500,
    val wordCount: Int = 0,
    val hookLine: String? = null,
    val ctaText: String? = null,
    val status: String = "DRAFT",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
