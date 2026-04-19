package com.ongo.domain.contentsource

import java.time.Instant

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val modifiedAt: Instant,
    val kind: Kind,
) {
    enum class Kind { FILE, FOLDER }

    fun isFolder(): Boolean = kind == Kind.FOLDER
    fun isVideo(): Boolean = kind == Kind.FILE && mimeType.startsWith("video/")
}
