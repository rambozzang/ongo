package com.ongo.domain.contentsource

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class DriveImportJobTest {
    @Test
    fun `percent returns 0 when fileSizeBytes is zero`() {
        val job = sampleJob(fileSizeBytes = 0, bytesTransferred = 0)
        assertEquals(0, job.percent())
    }

    @Test
    fun `percent returns 50 when half transferred`() {
        val job = sampleJob(fileSizeBytes = 1000, bytesTransferred = 500)
        assertEquals(50, job.percent())
    }

    @Test
    fun `percent caps at 100`() {
        val job = sampleJob(fileSizeBytes = 100, bytesTransferred = 100)
        assertEquals(100, job.percent())
    }

    private fun sampleJob(fileSizeBytes: Long, bytesTransferred: Long) = DriveImportJob(
        id = 1, videoId = 10, userId = 100, contentSourceId = 1,
        driveFileId = "f1", driveFileName = "a.mp4",
        fileSizeBytes = fileSizeBytes, bytesTransferred = bytesTransferred,
        status = DriveImportStatus.DOWNLOADING, s3Key = null, errorMessage = null,
        retryCount = 0, startedAt = Instant.now(), completedAt = null,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )
}
