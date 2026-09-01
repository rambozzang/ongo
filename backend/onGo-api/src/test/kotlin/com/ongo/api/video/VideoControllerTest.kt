package com.ongo.api.video

import com.ongo.api.video.dto.PresignedUploadRequest
import com.ongo.api.video.dto.PublishRequest
import com.ongo.api.video.dto.PlatformPublishConfig
import com.ongo.api.video.dto.RecyclePlatformRequest
import com.ongo.api.video.dto.RecycleRequest
import com.ongo.application.video.AssetNotPromotableException
import com.ongo.application.video.AssetToVideoUseCase
import com.ongo.application.video.CrossPlatformOptimizationUseCase
import com.ongo.application.video.PromotedVideo
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
import com.ongo.common.exception.BusinessException
import io.mockk.slot
import org.junit.jupiter.api.assertThrows
import com.ongo.application.video.VideoFeedResponse
import com.ongo.application.video.dto.OptimizationCheckRequest
import com.ongo.application.video.dto.OptimizationCheckResponse
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class VideoControllerTest {
    private val upload = mockk<UploadVideoUseCase>()
    private val publish = mockk<PublishVideoUseCase>()
    private val recycle = mockk<RecycleVideoUseCase>(relaxed = true)
    private val query = mockk<VideoQueryUseCase>(relaxed = true)
    private val feed = mockk<VideoFeedUseCase>(relaxed = true)
    private val optimization = mockk<CrossPlatformOptimizationUseCase>()
    private val fromAsset = mockk<AssetToVideoUseCase>()
    private val controller =
        VideoController(upload, publish, fromAsset, recycle, query, feed, optimization)

    /* ── 에셋 승격 ────────────────────────────────────────────────────── */

    /**
     * 경로가 `/{id}` 계열과 겹치지 않는 **고정 세그먼트**여야 한다. `/videos/{id}` 형태로
     * 열면 숫자가 아닌 값이나 다른 하위 경로와 해석이 엇갈린다.
     */
    @Test
    fun `에셋 승격은 고정 경로로 열려 있고 사용자와 에셋 id 를 그대로 넘긴다`() {
        every { fromAsset.promote(7L, 55L) } returns PromotedVideo(videoId = 91L)

        val response = controller.createFromAsset(7L, 55L)

        assertEquals(91L, response.body!!.data!!.videoId)
        verify(exactly = 1) { fromAsset.promote(7L, 55L) }
    }

    /** 거절 사유는 삼키지 않고 그대로 올린다 — 화면이 왜 안 되는지 말할 수 있어야 한다. */
    @Test
    fun `승격 거절은 컨트롤러가 삼키지 않는다`() {
        every { fromAsset.promote(7L, 55L) } throws AssetNotPromotableException("영상 에셋만 콘텐츠로 만들 수 있습니다.")

        val error = assertFailsWith<AssetNotPromotableException> { controller.createFromAsset(7L, 55L) }

        assertEquals("영상 에셋만 콘텐츠로 만들 수 있습니다.", error.message)
    }

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

    /**
     * 위임 계약은 그대로다 — 인증된 사용자를 유스케이스로 넘긴다. 다만 유스케이스가
     * 요금 미정으로 비활성이므로 컨트롤러는 그 실패를 **삼키지 않고 그대로 올려야** 한다.
     * 여기서 잡아 빈 응답을 200 으로 돌려주면 클라이언트는 최적화가 된 줄 안다.
     */
    @Test
    fun `ai optimization endpoint delegates the authenticated user and propagates the failure`() {
        val request = AiOptimizationRequest("원본", platforms = listOf(Platform.YOUTUBE))
        every { optimization.optimizeContent(7L, request) } throws
            BusinessException("FEATURE_NOT_AVAILABLE", "AI 크로스 플랫폼 최적화는 현재 제공하지 않습니다.")

        val e = assertFailsWith<BusinessException> { controller.optimizeContent(7L, request) }

        assertEquals("FEATURE_NOT_AVAILABLE", e.code)
        verify { optimization.optimizeContent(7L, request) }
    }

    // ── 피드 커서 파싱 ───────────────────────────────────────────────────────
    //
    // 플랫폼 페이지 토큰은 **불투명한 문자열**이라 `:` 이 들어갈 수 있다. 예전에는 서버가
    // `page.toString()` 을 토큰 자리에 넣어 2페이지부터 잘못된 목록을 돌려줬다.

    @Test
    fun `feed cursor is parsed per channel and forwarded`() {
        val tokens = slot<Map<Long, String>>()
        every { feed.getFeed(7L, null, 0, 20, "recent", capture(tokens)) } returns
            VideoFeedResponse(items = emptyList(), platforms = emptyList())

        controller.getVideoFeed(7L, null, 0, 20, "recent", listOf("11:ABC", "12:XYZ"))

        assertEquals(mapOf(11L to "ABC", 12L to "XYZ"), tokens.captured)
    }

    /** 토큰 안의 `:` 을 잘라 먹으면 다음 페이지가 어긋난다. */
    @Test
    fun `feed cursor keeps colons inside the opaque token`() {
        val tokens = slot<Map<Long, String>>()
        every { feed.getFeed(7L, null, 0, 20, "recent", capture(tokens)) } returns
            VideoFeedResponse(items = emptyList(), platforms = emptyList())

        controller.getVideoFeed(7L, null, 0, 20, "recent", listOf("11:AB:CD:EF"))

        assertEquals(mapOf(11L to "AB:CD:EF"), tokens.captured)
    }

    @Test
    fun `feed without cursor sends an empty token map`() {
        val tokens = slot<Map<Long, String>>()
        every { feed.getFeed(7L, null, 0, 20, "recent", capture(tokens)) } returns
            VideoFeedResponse(items = emptyList(), platforms = emptyList())

        controller.getVideoFeed(7L, null, 0, 20, "recent", null)

        assertEquals(emptyMap(), tokens.captured)
    }

    /** 잘못된 커서를 조용히 버리면 "다음 페이지" 가 첫 페이지를 다시 보여준다. */
    @Test
    fun `feed rejects a malformed cursor instead of ignoring it`() {
        for (bad in listOf("nope", "11", ":ABC", "11:")) {
            assertThrows<BusinessException> {
                controller.getVideoFeed(7L, null, 0, 20, "recent", listOf(bad))
            }
        }
    }
}
