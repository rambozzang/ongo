package com.ongo.api.videodownload

import com.ongo.application.videodownload.VideoDownloadResult
import com.ongo.application.videodownload.VideoDownloadUseCase
import com.ongo.common.ResData
import com.ongo.domain.videodownload.VideoDownloadProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class VideoDownloadControllerTest {
    private val useCase = mockk<VideoDownloadUseCase>()
    private val controller = VideoDownloadController(useCase)

    @Test
    fun `imports URL for authenticated user and returns created response`() {
        every { useCase.importVideo(12L, any()) } returns VideoDownloadResult(
            videoId = 99L,
            title = "가져온 영상",
            provider = VideoDownloadProvider.TIKTOK,
            fileUrl = "https://storage/video.mp4",
        )

        val response = controller.importUrl(
            12L,
            com.ongo.api.videodownload.dto.VideoDownloadRequest("https://www.tiktok.com/@a/video/1"),
        )

        assertEquals(201, response.statusCode.value())
        assertEquals(99L, response.body?.data?.videoId)
        assertEquals(VideoDownloadProvider.TIKTOK, response.body?.data?.provider)
    }
}
