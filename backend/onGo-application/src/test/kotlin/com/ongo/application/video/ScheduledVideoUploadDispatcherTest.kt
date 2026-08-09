package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class ScheduledVideoUploadDispatcherTest {
    private val uploads = mockk<VideoUploadRepository>()
    private val videos = mockk<VideoRepository>()
    private val metas = mockk<VideoPlatformMetaRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val storage = mockk<StorageService>()
    private val guard = mockk<UserWriteGuard>()

    @BeforeEach
    fun defaultRetryQueue() {
        every { uploads.findDueRetryUploads(any()) } returns emptyList()
    }

    private val video = Video(
        id = 101L,
        userId = 7L,
        title = "예약 영상",
        fileUrl = "https://storage.example/video.mp4",
    )
    private val upload = VideoUpload(
        id = 201L,
        videoId = 101L,
        platform = Platform.YOUTUBE,
        status = UploadStatus.UPLOADING,
        scheduledAt = LocalDateTime.now().minusMinutes(1),
    )

    private fun dispatcher() = ScheduledVideoUploadDispatcher(
        videoUploadRepository = uploads,
        videoRepository = videos,
        videoPlatformMetaRepository = metas,
        eventPublisher = publisher,
        storageService = storage,
        userWriteGuard = guard,
    )

    @Test
    fun `frozen account cannot be published by the independent due dispatcher`() {
        every { uploads.findDueScheduledUploads(any()) } returns listOf(upload)
        every { videos.findById(101L) } returns video
        every { storage.getFileUrl(101L) } returns video.fileUrl!!
        every { guard.requireWritable(7L, any(), any()) } throws AccountFrozenException()

        dispatcher().dispatchDueUploads()

        verify(exactly = 0) { publisher.publishEvent(any()) }
        verify(exactly = 0) { uploads.claim(any(), any(), any(), any()) }
    }

    @Test
    fun `active account emits an immediate event for a due upload`() {
        every { uploads.findDueScheduledUploads(any()) } returns listOf(upload)
        every { videos.findById(101L) } returns video
        every { storage.getFileUrl(101L) } returns video.fileUrl!!
        every { guard.requireWritable(7L, any(), any()) } returns Unit
        every { uploads.claim(201L, any(), any(), any()) } returns upload.copy(
            leaseOwner = "scheduled:201:worker",
            leaseUntil = LocalDateTime.now().plusMinutes(30),
        )

        dispatcher().dispatchDueUploads()

        val event = slot<VideoPublishEvent>()
        verify(exactly = 1) {
            publisher.publishEvent(capture(event))
        }
        assert(event.captured.videoId == 101L)
        assert(event.captured.platformConfigs.single().videoUploadId == 201L)
        assert(event.captured.platformConfigs.single().scheduledAt == null)
        assert(event.captured.platformConfigs.single().leaseOwner?.startsWith("scheduled:201:") == true)
        verify(exactly = 1) { uploads.claim(201L, any(), any(), any()) }
    }

    @Test
    fun `another instance winning the lease does not emit a duplicate event`() {
        every { uploads.findDueScheduledUploads(any()) } returns listOf(upload)
        every { videos.findById(101L) } returns video
        every { storage.getFileUrl(101L) } returns video.fileUrl!!
        every { guard.requireWritable(7L, any(), any()) } returns Unit
        every { uploads.claim(201L, any(), any(), any()) } returns null

        dispatcher().dispatchDueUploads()

        verify(exactly = 0) { publisher.publishEvent(any()) }
    }

    @Test
    fun `durable retry row is dispatched without treating it as a future schedule`() {
        val retry = upload.copy(
            scheduledAt = null,
            nextRetryAt = LocalDateTime.now().minusSeconds(1),
        )
        every { uploads.findDueScheduledUploads(any()) } returns emptyList()
        every { uploads.findDueRetryUploads(any()) } returns listOf(retry)
        every { videos.findById(101L) } returns video
        every { storage.getFileUrl(101L) } returns video.fileUrl!!
        every { guard.requireWritable(7L, any(), any()) } returns Unit
        every { uploads.claim(201L, any(), any(), any()) } returns retry.copy(
            leaseOwner = "scheduled:201:worker",
            leaseUntil = LocalDateTime.now().plusMinutes(30),
        )

        dispatcher().dispatchDueUploads()

        val event = slot<VideoPublishEvent>()
        verify(exactly = 1) { publisher.publishEvent(capture(event)) }
        assert(event.captured.platformConfigs.single().scheduledAt == null)
    }

    @Test
    fun `metadata failure after claim becomes unconfirmed without publishing`() {
        every { uploads.findDueScheduledUploads(any()) } returns listOf(upload)
        every { videos.findById(101L) } returns video
        every { storage.getFileUrl(101L) } returns video.fileUrl!!
        every { guard.requireWritable(7L, any(), any()) } returns Unit
        every { uploads.claim(201L, any(), any(), any()) } returns upload.copy(
            leaseOwner = "scheduled:201:worker",
            leaseUntil = LocalDateTime.now().plusMinutes(30),
        )
        every { uploads.updateOwned(any(), any()) } returns true
        every { metas.findByVideoUploadId(201L) } throws IllegalStateException("database unavailable")

        dispatcher().dispatchDueUploads()

        verify(exactly = 0) { publisher.publishEvent(any()) }
        verify {
            uploads.updateOwned(match { it.status == UploadStatus.UNCONFIRMED }, any())
        }
    }
}
