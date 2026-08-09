package com.ongo.application.ugc.shorts

import com.ongo.application.video.PlatformUploadStatus
import com.ongo.application.video.PublishResult
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ShortsPublishAdapterTest {

    private val publishVideoUseCase = mockk<PublishVideoUseCase>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val adapter = ShortsPublishAdapter(publishVideoUseCase, videoRepository, videoUploadRepository)

    @Test
    fun `encoded channel target resolves its own durable upload row`() {
        val userId = 42L
        val videoId = 904L
        val scheduledAt = Instant.parse("2026-03-01T09:00:00Z")
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "렌더 쇼츠",
            fileUrl = "https://storage.test/short.mp4",
        )
        every {
            publishVideoUseCase.publishVideo(userId, videoId, match { configs ->
                configs.single().platform == Platform.YOUTUBE &&
                    configs.single().channelId == 202L
            })
        } returns PublishResult(
            videoId = videoId,
            uploads = listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)),
        )
        every { videoUploadRepository.findByVideoIdAndChannelId(videoId, 202L) } returns VideoUpload(
            id = 802L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 202L,
            status = UploadStatus.UPLOADING,
        )
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) } returns VideoUpload(
            id = 801L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 201L,
            status = UploadStatus.PUBLISHED,
        )

        val result = adapter.publishAll(
            userId = userId,
            videoId = videoId,
            requests = listOf(ShortsPublishRequest("YOUTUBE#202", null, null, scheduledAt)),
        )

        assertEquals("YOUTUBE#202", result.single().platform)
        assertEquals(802L, result.single().videoUploadId)
        verify(exactly = 0) { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) }
    }
}
