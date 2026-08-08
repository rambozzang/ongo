package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.tiktok.TikTokApi
import com.ongo.infrastructure.external.tiktok.dto.TikTokCreatorPublishInfoResponse
import com.ongo.infrastructure.external.tiktok.dto.TikTokInitUploadResponse
import com.ongo.infrastructure.external.tiktok.dto.TikTokPublishStatusResponse
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TikTokStreamWriterTest {

    private val tikTokApi = mockk<TikTokApi>()
    private val fileTransferHelper = mockk<PlatformFileTransferHelper>()
    private lateinit var writer: TikTokStreamWriter

    private val accessToken = "tiktok-access-token"
    private val publishId = "publish-123"
    private val publicVideoId = "video-456"

    @BeforeEach
    fun setUp() {
        writer = TikTokStreamWriter(
            tikTokApi = tikTokApi,
            fileTransferHelper = fileTransferHelper,
            statusPollIntervalMs = 0,
            statusPollMaxAttempts = 2,
        )
        every {
            tikTokApi.queryCreatorPublishInfo("Bearer $accessToken")
        } returns TikTokCreatorPublishInfoResponse(
            data = TikTokCreatorPublishInfoResponse.CreatorPublishData(
                privacyLevelOptions = listOf("PUBLIC_TO_EVERYONE"),
            ),
            error = null,
        )
        every {
            tikTokApi.initVideoUpload("Bearer $accessToken", any())
        } returns TikTokInitUploadResponse(
            data = TikTokInitUploadResponse.UploadData(
                publishId = publishId,
                uploadUrl = "https://tiktok.example/upload",
            ),
            error = null,
        )
        justRun { fileTransferHelper.uploadChunkedToTikTok(any(), any(), any()) }
    }

    @Test
    fun `파일 전송 후 PUBLISH_COMPLETE 확인 시 실제 영상 ID와 URL을 반환한다`() {
        every {
            tikTokApi.fetchPublishStatus("Bearer $accessToken", any())
        } returns TikTokPublishStatusResponse(
            data = TikTokPublishStatusResponse.StatusData(
                status = "PUBLISH_COMPLETE",
                publicPostId = listOf(publicVideoId),
                failReason = null,
            ),
            error = null,
        )

        writer.initSession(
            meta = VideoPlatformMeta(videoUploadId = 1L, title = "테스트", visibility = Visibility.PUBLIC),
            accessToken = accessToken,
            platformChannelId = "creator_name",
            fileSize = 4,
            scheduledAt = null,
        )
        writer.writeChunk("test".toByteArray(), 0, 4)

        val result = writer.complete()

        assertThat(result.success).isTrue()
        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo(publicVideoId)
        assertThat(result.platformUrl).isEqualTo("https://www.tiktok.com/@creator_name/video/$publicVideoId")
        verify(exactly = 1) { tikTokApi.fetchPublishStatus("Bearer $accessToken", any()) }
    }
}
