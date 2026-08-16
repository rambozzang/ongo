package com.ongo.api.video

import com.ongo.api.video.dto.PresignedUploadRequest
import com.ongo.api.video.dto.PublishRequest
import com.ongo.api.video.dto.PlatformPublishConfig
import com.ongo.api.video.dto.RecyclePlatformRequest
import com.ongo.api.video.dto.RecycleRequest
import com.ongo.application.video.CrossPlatformOptimizationUseCase
import com.ongo.application.video.PlatformUploadStatus
import com.ongo.application.video.PresignedUploadResult
import com.ongo.application.video.PublishResult
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.RecycleVideoUseCase
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.application.video.VideoFeedUseCase
import com.ongo.application.video.VideoQueryUseCase
import com.ongo.application.video.VideoDeletionResult
import com.ongo.application.video.dto.AiOptimizationRequest
import com.ongo.application.video.dto.AiOptimizationResponse
import com.ongo.application.video.dto.OptimizationCheckRequest
import com.ongo.application.video.dto.OptimizationCheckResponse
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class VideoControllerTest {
    private val upload = mockk<UploadVideoUseCase>()
    private val publish = mockk<PublishVideoUseCase>()
    private val recycle = mockk<RecycleVideoUseCase>(relaxed = true)
    private val query = mockk<VideoQueryUseCase>(relaxed = true)
    private val feed = mockk<VideoFeedUseCase>(relaxed = true)
    private val optimization = mockk<CrossPlatformOptimizationUseCase>()
    private val controller = VideoController(upload, publish, recycle, query, feed, optimization)

    @Test
    fun `presigned upload lifecycle delegates ownership and returns stable responses`() {
        every { upload.initiatePresignedUpload(7L, "clip.mp4", "video/mp4", 123L) } returns
            PresignedUploadResult(11L, "https://storage.example/put/11")
        justRun { upload.confirmPresignedUpload(7L, 11L) }

        val initiated = controller.initiateUpload(
            7L,
            PresignedUploadRequest("clip.mp4", 123L, "video/mp4"),
        )
        val confirmed = controller.confirmUpload(7L, 11L)

        assertEquals(200, initiated.statusCode.value())
        assertEquals(11L, initiated.body?.data?.videoId)
        assertEquals("https://storage.example/put/11", initiated.body?.data?.uploadUrl)
        assertEquals(200, confirmed.statusCode.value())
        verify(exactly = 1) { upload.confirmPresignedUpload(7L, 11L) }
    }

    @Test
    fun `publish maps per-platform metadata and preserves final status`() {
        every { publish.publishVideo(7L, 21L, any()) } returns PublishResult(
            videoId = 21L,
            uploads = listOf(
                PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.PUBLISHED),
                PlatformUploadStatus(Platform.TIKTOK, UploadStatus.UNCONFIRMED, "결과 확인 필요"),
            ),
        )

        val response = controller.publishVideo(
            7L,
            21L,
            PublishRequest(
                platforms = listOf(
                    PlatformPublishConfig(
                        platform = Platform.YOUTUBE,
                        title = "채널 제목",
                        description = "설명",
                        tags = listOf("tag"),
                    ),
                    PlatformPublishConfig(platform = Platform.TIKTOK, title = "짧은 제목"),
                )
            ),
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(21L, response.body?.data?.videoId)
        assertEquals(UploadStatus.PUBLISHED, response.body?.data?.uploads?.first()?.status)
        assertEquals("결과 확인 필요", response.body?.data?.uploads?.last()?.errorMessage)
        verify {
            publish.publishVideo(7L, 21L, match {
                it.size == 2 &&
                    it.first().platform == Platform.YOUTUBE &&
                    it.first().title == "채널 제목" &&
                    it.first().tags == listOf("tag") &&
                    it.last().platform == Platform.TIKTOK
            })
        }
    }

    @Test
    fun `retry recheck delete and optimization endpoints delegate without changing semantics`() {
        justRun { publish.retryUpload(7L, 21L, "YOUTUBE") }
        justRun { publish.recheckUpload(7L, 21L, "TIKTOK") }
        every { query.deleteVideo(7L, 21L) } returns VideoDeletionResult(21L)
        val request = OptimizationCheckRequest("제목", tags = listOf("one"))
        every { optimization.checkOptimization(request) } returns OptimizationCheckResponse(emptyList())

        assertEquals(200, controller.retryUpload(7L, 21L, "YOUTUBE").statusCode.value())
        assertEquals(200, controller.recheckUpload(7L, 21L, "TIKTOK").statusCode.value())
        assertEquals(200, controller.deleteVideo(7L, 21L).statusCode.value())
        assertEquals(200, controller.checkOptimization(request).statusCode.value())

        verify { publish.retryUpload(7L, 21L, "YOUTUBE") }
        verify { publish.recheckUpload(7L, 21L, "TIKTOK") }
        verify { query.deleteVideo(7L, 21L) }
        verify { optimization.checkOptimization(request) }
    }

    @Test
    fun `recycle preserves per-platform overrides and starts an independent publish`() {
        every { recycle.recycle(7L, 21L, "재활용 제목", "설명", listOf("tag"), "creator", any()) } returns
            PublishResult(22L, listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)))

        val response = controller.recycleVideo(
            7L,
            21L,
            RecycleRequest(
                title = "재활용 제목",
                description = "설명",
                tags = listOf("tag"),
                category = "creator",
                platforms = listOf(RecyclePlatformRequest(platform = Platform.YOUTUBE, title = "채널 제목")),
            ),
        )

        assertEquals(200, response.statusCode.value())
        assertEquals(22L, response.body?.data?.videoId)
        verify {
            recycle.recycle(7L, 21L, "재활용 제목", "설명", listOf("tag"), "creator", match {
                it.single().platform == Platform.YOUTUBE && it.single().title == "채널 제목"
            })
        }
    }

    @Test
    fun `ai optimization endpoint delegates the authenticated user`() {
        val request = AiOptimizationRequest("원본", platforms = listOf(Platform.YOUTUBE))
        val result = AiOptimizationResponse(request, emptyMap())
        every { optimization.optimizeContent(7L, request) } returns result

        val response = controller.optimizeContent(7L, request)

        assertEquals(200, response.statusCode.value())
        assertEquals(result, response.body?.data)
        verify { optimization.optimizeContent(7L, request) }
    }
}
