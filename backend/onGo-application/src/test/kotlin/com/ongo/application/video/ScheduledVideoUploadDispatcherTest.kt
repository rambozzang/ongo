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
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class ScheduledVideoUploadDispatcherTest {
    private val uploads = mockk<VideoUploadRepository>()
    private val videos = mockk<VideoRepository>()
    private val metas = mockk<VideoPlatformMetaRepository>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val storage = mockk<StorageService>()
    private val guard = mockk<UserWriteGuard>()

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
    }

    @Test
    fun `active account emits an immediate event for a due upload`() {
        every { uploads.findDueScheduledUploads(any()) } returns listOf(upload)
        every { videos.findById(101L) } returns video
        every { storage.getFileUrl(101L) } returns video.fileUrl!!
        every { guard.requireWritable(7L, any(), any()) } returns Unit

        dispatcher().dispatchDueUploads()

        verify(exactly = 1) {
            publisher.publishEvent(match<VideoPublishEvent> {
                it.videoId == 101L &&
                    it.platformConfigs.single().videoUploadId == 201L &&
                    it.platformConfigs.single().scheduledAt == null
            })
        }
    }
}
