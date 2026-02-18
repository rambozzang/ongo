package com.ongo.domain.video

import java.time.LocalDateTime

data class ContentImage(
    val id: Long? = null,
    val videoId: Long,
    val imageUrl: String,
    val displayOrder: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val fileSizeBytes: Long? = null,
    val originalFilename: String? = null,
    val contentType: String? = null,
    val createdAt: LocalDateTime? = null,
)
