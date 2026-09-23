package com.ongo.api.videodownload

import com.ongo.application.videodownload.VideoDownloadResult
import com.ongo.application.videodownload.VideoDownloadUseCase
import com.ongo.application.videodownload.VideoImportJobService
import com.ongo.common.ResData
import com.ongo.domain.videodownload.VideoDownloadProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class VideoDownloadControllerTest {
    private val useCase = mockk<VideoDownloadUseCase>()
    private val jobs = mockk<VideoImportJobService>()
    private val controller = VideoDownloadController(useCase, jobs)

    /**
     * 가져오기는 수십 분이 걸릴 수 있어 요청 안에서 끝내지 않는다(nginx 60초 제한에 걸려 사용자는
     * 성공한 작업을 504 실패로 봤다). 작업 번호를 202 로 바로 돌려준다.
     */
    @Test
    fun `starts an import job and returns accepted with job id`() {
        every { jobs.start(12L, any()) } returns VideoImportJobService.Job(
            id = "job-1", userId = 12L, status = VideoImportJobService.Status.QUEUED, createdAt = Instant.EPOCH,
        )

        val response = controller.importUrl(
            12L,
            com.ongo.api.videodownload.dto.VideoDownloadRequest("https://www.tiktok.com/@a/video/1"),
        )

        assertEquals(202, response.statusCode.value())
        assertEquals("job-1", response.body?.data?.jobId)
        assertEquals("QUEUED", response.body?.data?.status)
        verify(exactly = 0) { useCase.importVideo(any(), any()) }
    }

    @Test
    fun `reports finished job with the imported video`() {
        every { jobs.get(12L, "job-1") } returns VideoImportJobService.Job(
            id = "job-1", userId = 12L, status = VideoImportJobService.Status.SUCCEEDED, createdAt = Instant.EPOCH,
            result = VideoDownloadResult(
                videoId = 99L, title = "가져온 영상", provider = VideoDownloadProvider.TIKTOK, fileUrl = "https://storage/video.mp4",
            ),
        )

        val body = controller.importJob(12L, "job-1").body?.data

        assertEquals("SUCCEEDED", body?.status)
        assertEquals(99L, body?.result?.videoId)
        assertEquals(VideoDownloadProvider.TIKTOK, body?.result?.provider)
    }

    @Test
    fun `reports failed job with its error code`() {
        every { jobs.get(12L, "job-2") } returns VideoImportJobService.Job(
            id = "job-2", userId = 12L, status = VideoImportJobService.Status.FAILED, createdAt = Instant.EPOCH,
            errorCode = "VIDEO_DOWNLOAD_SIZE_INVALID", errorMessage = "최대 10GB 까지 가져올 수 있습니다.",
        )

        val body = controller.importJob(12L, "job-2").body?.data

        assertEquals("FAILED", body?.status)
        assertEquals("VIDEO_DOWNLOAD_SIZE_INVALID", body?.errorCode)
        assertEquals(null, body?.result)
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
