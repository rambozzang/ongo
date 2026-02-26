package com.ongo.domain.contentlibrary

import java.time.Instant

data class LibraryFolder(
    val id: Long = 0,
    val userId: Long,
    val name: String,
    val parentId: Long? = null,
    val color: String = "#3B82F6",
    val createdAt: Instant = Instant.now(),
)
