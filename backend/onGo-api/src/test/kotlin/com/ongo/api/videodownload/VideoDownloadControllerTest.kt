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

    @Test
    fun `reports extractor availability without failing when it is missing`() {
        every { useCase.checkAvailability() } returns
            com.ongo.application.videodownload.DownloaderAvailability(
                available = false,
                reason = "영상 URL 가져오기를 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
            )

        val response = controller.importUrlAvailability()

        // 쓸 수 없다는 것은 오류가 아니라 상태다. 200 으로 내려야 화면이 진입점을
        // 감추거나 비활성화할 수 있다. 4xx/5xx 로 내리면 화면은 "장애"로 다룬다.
        assertEquals(200, response.statusCode.value())
        assertEquals(false, response.body?.data?.available)
        assertEquals(true, response.body?.success)
    }

    @Test
    fun `reports extractor availability when present`() {
        every { useCase.checkAvailability() } returns
            com.ongo.application.videodownload.DownloaderAvailability(available = true)

        val response = controller.importUrlAvailability()

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body?.data?.available)
        assertEquals(null, response.body?.data?.reason)
    }
}
