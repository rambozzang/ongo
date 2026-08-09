package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.lock.DistributedLockPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class VideoUploadLeaseReaperTest {
    private val uploads = mockk<VideoUploadRepository>()
    private val videos = mockk<VideoRepository>()
    private val lock = mockk<DistributedLockPort>()
    private val reaper = VideoUploadLeaseReaper(uploads, videos, lock)

    @Test
    fun `expired upload without a poll token becomes unconfirmed instead of being resent`() {
        val recovered = VideoUpload(
            id = 11L,
            videoId = 7L,
            platform = Platform.YOUTUBE,
            status = UploadStatus.UNCONFIRMED,
            errorMessage = "작업 lease가 만료되어 게시 결과 확인이 필요합니다.",
        )
        every { uploads.recoverExpiredLeases(any()) } returns listOf(recovered)
        every { uploads.findByVideoId(7L) } returns listOf(recovered)
        every { videos.findById(7L) } returns Video(id = 7L, userId = 3L, title = "영상")
        every { videos.update(any()) } answers { firstArg() }
        every { lock.withLock(any(), any<() -> Unit>()) } answers { secondArg<() -> Unit>()(); true }

        reaper.recoverExpiredLeases()

        val savedVideo = slot<Video>()
        verify { videos.update(capture(savedVideo)) }
        assertEquals(UploadStatus.UNCONFIRMED, savedVideo.captured.status)
        verify(exactly = 0) { uploads.claim(any(), any(), any(), any()) }
    }

    @Test
    fun `expired accepted upload keeps its poll path available`() {
        val recovered = VideoUpload(
            id = 12L,
            videoId = 8L,
            platform = Platform.INSTAGRAM,
            status = UploadStatus.PROCESSING,
            pollToken = "container-8",
        )
        every { uploads.recoverExpiredLeases(any()) } returns listOf(recovered)
        every { uploads.findByVideoId(8L) } returns listOf(recovered)
        every { videos.findById(8L) } returns Video(id = 8L, userId = 3L, title = "영상")
        every { videos.update(any()) } answers { firstArg() }
        every { lock.withLock(any(), any<() -> Unit>()) } answers { secondArg<() -> Unit>()(); true }

        reaper.recoverExpiredLeases()

        val savedVideo = slot<Video>()
        verify { videos.update(capture(savedVideo)) }
        assertEquals(UploadStatus.PROCESSING, savedVideo.captured.status)
    }
}
