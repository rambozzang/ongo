package com.ongo.domain.contentlibrary

import java.time.Instant

data class LibraryItem(
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val type: String,
    val platform: String? = null,
    val thumbnailUrl: String? = null,
    val fileSize: Long = 0,
    val tags: List<String> = emptyList(),
    val folderId: Long? = null,
    val uploadedAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
