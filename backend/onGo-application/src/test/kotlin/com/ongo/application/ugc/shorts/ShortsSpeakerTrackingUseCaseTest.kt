package com.ongo.application.ugc.shorts

import com.ongo.application.ai.ChatClientRegistry
import com.ongo.application.ai.economics.AiUnitEconomics
import com.ongo.application.credit.CreditService
import com.ongo.application.ugc.shorts.dto.ClipEditRequest
import com.ongo.application.ugc.shorts.dto.ClipEditResponse
import com.ongo.application.ugc.shorts.dto.ClipEditorResponse
import com.ongo.application.video.StorageService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.AiProvider
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.FrameSampler
import com.ongo.domain.ugc.shorts.FrameSheet
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.client.ChatClient
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 화자 추적은 **적용했을 때만** 크레딧을 가져가고, 저장은 편집기와 같은 길로 한다. */
class ShortsSpeakerTrackingUseCaseTest {

    private val clipEdit = mockk<ShortsClipEditUseCase>()
    private val runs = mockk<PipelineRunRepository>()
    private val videos = mockk<VideoRepository>()
    private val storage = mockk<StorageService>()
    private val sampler = mockk<FrameSampler>()
    private val registry = mockk<ChatClientRegistry>()
    private val credits = mockk<CreditService>()
    private val client = mockk<ChatClient>()

    private val useCase = ShortsSpeakerTrackingUseCase(clipEdit, runs, videos, storage, sampler, registry, credits, AiUnitEconomics())

    private fun editor(editable: Boolean = true) = ClipEditorResponse(
        clipId = 3, runId = 2, seq = 1, startMs = 10_000, endMs = 40_000, title = null, caption = null, status = "RENDER_READY",
        subtitles = emptyList(), cropJson = null, sourceDurationMs = 600_000, editable = editable,
        notEditableReason = if (editable) null else "예약된 클립", hasRenderedVideo = false,
    )

    private val sheet = FrameSheet(byteArrayOf(1), "image/jpeg", 4, 3, (0 until 12).map { it * 2_500L }, 1920, 1080)

    @BeforeEach
    fun setUp() {
        every { clipEdit.getEditor(7, 1, 2, 3) } returns editor()
        every { registry.isProviderAvailable(AiProvider.QWEN) } returns true
        every { registry.getClient(AiProvider.QWEN) } returns client
        every { runs.findById(2) } returns PipelineRun(id = 2, workspaceId = 1, userId = 7, sourceVideoId = 55)
        every { videos.findById(55) } returns Video(id = 55, userId = 7, title = "롱폼", fileUrl = "https://s/55.mp4")
        every { storage.getFileUrl(55, "https://s/55.mp4") } returns "https://signed/55.mp4"
        every { sampler.contactSheet("https://signed/55.mp4", 10_000, 40_000) } returns sheet
        every { credits.withCredits(any(), any(), any(), any<() -> Any>()) } answers { lastArg<() -> Any>().invoke() }
    }

    private fun modelSees(vararg tiles: SpeakerTrackResult.Tile) {
        every {
            client.prompt().user(any<Consumer<ChatClient.PromptUserSpec>>()).call().entity(SpeakerTrackResult::class.java)
        } returns SpeakerTrackResult(tiles.toList())
    }

    @Test
    fun `편집할 수 없는 클립은 크레딧을 받기 전에 거절한다`() {
        every { clipEdit.getEditor(7, 1, 2, 3) } returns editor(editable = false)

        val error = assertThrows<BusinessException> { useCase.track(7, 1, 2, 3) }

        assertEquals("SHORTS_CLIP_NOT_EDITABLE", error.code)
        verify(exactly = 0) { credits.withCredits(any(), any(), any(), any<() -> Any>()) }
    }

    @Test
    fun `인물을 충분히 찾지 못하면 크레딧 블록 안에서 실패해 환불되고 크롭은 바꾸지 않는다`() {
        modelSees(SpeakerTrackResult.Tile(1, true, 0.5, 0.3))

        val error = assertThrows<BusinessException> { useCase.track(7, 1, 2, 3) }

        assertEquals("SHORTS_TRACK_NOT_FOUND", error.code)
        verify(exactly = 1) { credits.withCredits(7, useCase.creditCost(), AiFeature.SHORTS_SPEAKER_TRACK.name, any<() -> Any>()) }
        verify(exactly = 0) { clipEdit.edit(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `찾으면 이동 크롭을 원본 크기와 함께 편집기 저장 경로로 넘긴다`() {
        modelSees(*(1..12).map { SpeakerTrackResult.Tile(it, true, if (it <= 6) 0.25 else 0.8, 0.3) }.toTypedArray())
        val request = slot<ClipEditRequest>()
        every { clipEdit.edit(7, 1, 2, 3, capture(request)) } returns ClipEditResponse(editor(), needsRerender = true, uncoveredSubtitleSpan = false)

        val response = useCase.track(7, 1, 2, 3)

        assertEquals(12, response.foundTiles)
        assertEquals(useCase.creditCost(), response.creditsCharged)
        assertEquals(1920, request.captured.sourceWidth)
        assertEquals(1080, request.captured.sourceHeight)
        val crop = request.captured.crop!!
        assertEquals("CROP", crop["mode"])
        assertTrue((crop["keyframes"] as List<*>).size >= 2, "$crop")
    }

    @Test
    fun `크레딧은 비전 호출 원가를 덮는다`() {
        assertTrue(useCase.creditCost() >= AiUnitEconomics().creditsForVisionCall(images = 1))
        assertTrue(useCase.creditCost() >= AiFeature.SHORTS_SPEAKER_TRACK.creditCost)
    }
}
