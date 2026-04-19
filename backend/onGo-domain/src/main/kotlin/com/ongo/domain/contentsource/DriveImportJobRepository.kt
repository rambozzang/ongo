package com.ongo.domain.contentsource

interface DriveImportJobRepository {
    fun findById(id: Long): DriveImportJob?
    fun findByVideoId(videoId: Long): DriveImportJob?
    fun findActiveByUserAndFileId(userId: Long, driveFileId: String): List<DriveImportJob>
    fun countActiveByUser(userId: Long): Int
    fun listActiveByUser(userId: Long): List<DriveImportJob>
    fun listStale(olderThanSeconds: Long): List<DriveImportJob>
    fun save(job: DriveImportJob): DriveImportJob
    fun updateStatus(id: Long, status: DriveImportStatus, errorMessage: String?)
    fun updateProgress(id: Long, bytesTransferred: Long)
    fun markCompleted(id: Long, s3Key: String)
}
