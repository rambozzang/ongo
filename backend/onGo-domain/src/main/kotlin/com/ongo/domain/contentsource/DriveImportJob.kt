package com.ongo.domain.contentsource

import java.time.Instant

data class DriveImportJob(
    val id: Long,
    val videoId: Long,
    val userId: Long,
    val contentSourceId: Long,
    val driveFileId: String,
    val driveFileName: String,
    val fileSizeBytes: Long,
    val bytesTransferred: Long,
    val status: DriveImportStatus,
    val s3Key: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun percent(): Int =
        if (fileSizeBytes == 0L) 0 else ((bytesTransferred * 100) / fileSizeBytes).toInt().coerceIn(0, 100)
}
