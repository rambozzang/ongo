package com.ongo.application.ugc

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
import kotlin.test.assertEquals

class CampaignPublishAdapterTest {

    private val publishVideoUseCase = mockk<PublishVideoUseCase>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val adapter = CampaignPublishAdapter(publishVideoUseCase, videoRepository, videoUploadRepository)

    @Test
    fun `campaign publish preserves encoded account targets`() {
        val creatorId = 42L
        val videoId = 905L
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = creatorId,
            title = "캠페인 영상",
            fileUrl = "https://storage.test/campaign.mp4",
        )
        every {
            publishVideoUseCase.publishVideo(creatorId, videoId, match { configs ->
                configs.single().platform == Platform.YOUTUBE && configs.single().channelId == 202L
            })
        } returns PublishResult(
            videoId = videoId,
            uploads = listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)),
        )
        every { videoUploadRepository.findByVideoIdAndChannelId(videoId, 202L) } returns VideoUpload(
            id = 902L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 202L,
            status = UploadStatus.UPLOADING,
        )
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) } returns VideoUpload(
            id = 901L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 201L,
            status = UploadStatus.PUBLISHED,
        )

        val result = adapter.publish(creatorId, videoId, listOf("YOUTUBE#202"))

        assertEquals("YOUTUBE#202", result.single().platform)
        assertEquals(902L, result.single().videoUploadId)
        verify(exactly = 0) { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) }
    }
}
