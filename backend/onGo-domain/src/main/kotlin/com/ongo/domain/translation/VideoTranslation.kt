package com.ongo.domain.translation

import java.time.LocalDateTime

data class VideoTranslation(
    val id: Long? = null,
    val videoId: Long,
    val language: String,
    val title: String? = null,
    val description: String? = null,
    val tags: String? = "[]",
    val subtitleContent: String? = null,
    val status: String = "PENDING",
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
