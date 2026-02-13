package com.ongo.domain.asset

import java.time.LocalDateTime

data class Asset(
    val id: Long? = null,
    val userId: Long,
    val filename: String,
    val originalFilename: String? = null,
    val fileUrl: String,
    val fileType: String,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val tags: List<String> = emptyList(),
    val folder: String = "default",
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Int? = null,
    val createdAt: LocalDateTime? = null,
)
