package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ClipEditRequest
import com.ongo.application.ugc.shorts.dto.PlatformMetaEdit
import com.ongo.application.ugc.shorts.dto.SubtitleLineEdit
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.ClipHookRepository
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsClipRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJob
import com.ongo.domain.ugc.shorts.ShortsRenderJobRepository
import com.ongo.domain.ugc.shorts.ShortsRenderJobStatus
import com.ongo.domain.ugc.shorts.ShortsTemplateRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.workspace.Workspace
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 클립 편집기의 실제 저장 흐름. 확인하는 것:
 * - 화면을 바꾸는 편집은 렌더 지시서를 새로 만들고 이전 결과(작업·연결 영상)를 버린다
 * - 제목·설명·플랫폼 메타만 바꾸면 렌더를 무효화하지 않는다
 * - 실행 중·렌더 중·예약 후에는 거절하고 아무것도 저장하지 않는다
 * - 다른 워크스페이스의 클립은 존재도 드러내지 않는다
 */
class ShortsClipEditUseCaseTest {

    private val workspaceRepository = mockk<WorkspaceRepository>()
    private val runRepository = mockk<PipelineRunRepository>()
    private val clipRepository = mockk<ShortsClipRepository>()
    private val hookRepository = mockk<ClipHookRepository>()
    private val renderJobRepository = mockk<ShortsRenderJobRepository>()
    private val templateRepository = mockk<ShortsTemplateRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val specBuilder = ShortsRenderSpecBuilder()

    private val useCase = ShortsClipEditUseCase(
        workspaceRepository, runRepository, clipRepository, hookRepository,
        renderJobRepository, templateRepository, videoRepository, specBuilder,
    )

    private val run = PipelineRun(
        id = 1, workspaceId = 10, userId = 7, sourceVideoId = 5,
        status = PipelineRunStatus.AWAITING_SCHEDULE, sourceDurationMs = 600_000,
    )

    private val subtitleJson = ShortsClipEditPolicy.toJson(
        listOf(
            ShortsRenderSpecBuilder.SubtitleLine(0, 2_000, "첫 줄", listOf(CaptionWord(0, 900, "첫"), CaptionWord(1_000, 1_900, "줄"))),
            ShortsRenderSpecBuilder.SubtitleLine(5_000, 8_000, "둘째 줄"),
        ),
    )

    /** 렌더까지 끝난 클립: 원본 60~90초. */
    private val renderedClip = ShortsClip(
        id = 3, runId = 1, seq = 1, startMs = 60_000, endMs = 90_000,
        title = "제목", caption = "설명", subtitleJson = subtitleJson,
        cropJson = null, renderSpec = """{"old":true}""", status = ClipStatus.RENDERED, renderedVideoId = 99,
    )

    private val saved = slot<ShortsClip>()

    @BeforeEach
    fun setUp() {
        every { workspaceRepository.findAccessibleByUserId(7) } returns listOf(Workspace(id = 10, ownerId = 7, name = "ws", slug = "ws"))
        every { runRepository.findById(1) } returns run
        every { clipRepository.findById(3) } returns renderedClip
        every { clipRepository.update(capture(saved)) } answers { saved.captured }
        every { hookRepository.findByClipIds(any()) } returns emptyList()
        every { templateRepository.findByWorkspace(10) } returns emptyList()
        every { videoRepository.findById(5) } returns Video(id = 5, userId = 7, title = "원본", fileUrl = "https://cdn.example.com/source.mp4")
        every { renderJobRepository.findByRunAndClip(1, 3) } returns job(ShortsRenderJobStatus.COMPLETED)
        every { renderJobRepository.deleteFinished(1, 3) } returns true
    }

    private fun job(status: ShortsRenderJobStatus) = ShortsRenderJob(id = "j", runId = 1, clipId = 3, status = status)

    @Test
    fun `구간을 다듬으면 자막을 옮기고 렌더 지시서를 새로 만들며 이전 렌더 결과를 버린다`() {
        val result = useCase.edit(7, 10, 1, 3, ClipEditRequest(startMs = 61_000, endMs = 85_000))

        val clip = saved.captured
        assertEquals(61_000, clip.startMs)
        assertEquals(85_000, clip.endMs)
        assertEquals(ClipStatus.RENDER_READY, clip.status)
        assertNull(clip.renderedVideoId)
        assertNotEquals("""{"old":true}""", clip.renderSpec)
        val spec = specBuilder.parseSpec(clip.renderSpec!!)
        assertEquals(61_000, spec.startMs)
        assertEquals(85_000, spec.endMs)
        // 첫 줄 0~2초 → -1~1초 → 0~1초로 잘리고, 잘려 나간 단어 "첫" 은 빠진다
        assertEquals(listOf(0L to 1_000L, 4_000L to 7_000L), spec.subtitles.map { it.startMs to it.endMs })
        assertEquals(listOf("줄"), spec.subtitles[0].words.map { it.text })
        assertEquals(0L, spec.subtitles[0].words.single().startMs)
        verify(exactly = 1) { renderJobRepository.deleteFinished(1, 3) }
        assertTrue(result.needsRerender)
        assertFalse(result.editor.hasRenderedVideo)
    }

    @Test
    fun `제목·설명·플랫폼 메타만 바꾸면 렌더 결과를 그대로 둔다`() {
        val result = useCase.edit(
            7, 10, 1, 3,
            ClipEditRequest(
                title = " 새 제목 ",
                caption = "",
                platformMeta = listOf(PlatformMetaEdit("YOUTUBE", "유튜브 제목", "유튜브 설명", listOf("#태그"))),
            ),
        )

        val clip = saved.captured
        assertEquals("새 제목", clip.title)
        assertNull(clip.caption) // 빈 설명은 "없음"
        assertEquals("""{"old":true}""", clip.renderSpec)
        assertEquals(99, clip.renderedVideoId)
        assertEquals(ClipStatus.RENDERED, clip.status)
        assertEquals(
            listOf(ShortsPlatformMeta.Entry("YOUTUBE", "유튜브 제목", "유튜브 설명", listOf("태그"))),
            ShortsPlatformMeta.parse(clip.platformMetaJson),
        )
        verify(exactly = 0) { renderJobRepository.deleteFinished(any(), any()) }
        assertFalse(result.needsRerender)
    }

    @Test
    fun `자막 문구를 고치면 그 줄의 단어 시각을 지우고 저장한다`() {
        useCase.edit(
            7, 10, 1, 3,
            ClipEditRequest(subtitles = listOf(SubtitleLineEdit(0, 2_000, "고친 첫 줄"), SubtitleLineEdit(5_000, 8_000, "둘째 줄"))),
        )

        val lines = specBuilder.parseSubtitles(saved.captured.subtitleJson)
        assertEquals(listOf("고친 첫 줄", "둘째 줄"), lines.map { it.text })
        assertTrue(lines.all { it.words.isEmpty() })
        assertEquals(ClipStatus.RENDER_READY, saved.captured.status)
    }

    @Test
    fun `크롭은 ShortsCropPolicy 로 정규화한 값 그대로 저장한다`() {
        val crop = mapOf<String, Any?>("x" to 100, "y" to 0, "width" to 300, "height" to 500)

        useCase.edit(7, 10, 1, 3, ClipEditRequest(crop = crop, sourceWidth = 1280, sourceHeight = 720))

        assertEquals(
            ShortsCropPolicy.normalizeJson("""{"x":100,"y":0,"width":300,"height":500}""", 1280, 720),
            saved.captured.cropJson,
        )
    }

    @Test
    fun `깨진 크롭은 SHORTS_CROP_INVALID 로 거절하고 저장하지 않는다`() {
        val ex = assertFailsWith<BusinessException> {
            useCase.edit(7, 10, 1, 3, ClipEditRequest(crop = mapOf("x" to 1)))
        }
        assertEquals("SHORTS_CROP_INVALID", ex.code)
        verify(exactly = 0) { clipRepository.update(any()) }
    }

    @Test
    fun `실행 중·렌더 중·예약 후·버린 클립은 편집할 수 없다`() {
        val blocked = listOf(
            { every { runRepository.findById(1) } returns run.copy(status = PipelineRunStatus.RUNNING) },
            { every { renderJobRepository.findByRunAndClip(1, 3) } returns job(ShortsRenderJobStatus.RUNNING) },
            { every { renderJobRepository.findByRunAndClip(1, 3) } returns job(ShortsRenderJobStatus.QUEUED) },
            { every { clipRepository.findById(3) } returns renderedClip.copy(status = ClipStatus.SCHEDULED) },
            { every { clipRepository.findById(3) } returns renderedClip.copy(status = ClipStatus.PUBLISHED) },
            { every { clipRepository.findById(3) } returns renderedClip.copy(status = ClipStatus.DISCARDED) },
        )
        blocked.forEach { arrange ->
            setUp()
            arrange()
            val ex = assertFailsWith<BusinessException> { useCase.edit(7, 10, 1, 3, ClipEditRequest(title = "x")) }
            assertEquals("SHORTS_CLIP_NOT_EDITABLE", ex.code)
            val editor = useCase.getEditor(7, 10, 1, 3)
            assertFalse(editor.editable)
            assertTrue(!editor.notEditableReason.isNullOrBlank())
        }
        verify(exactly = 0) { clipRepository.update(any()) }
    }

    @Test
    fun `저장하는 사이 렌더가 시작됐으면 실패시켜 트랜잭션을 되돌린다`() {
        every { renderJobRepository.deleteFinished(1, 3) } returns false

        val ex = assertFailsWith<BusinessException> { useCase.edit(7, 10, 1, 3, ClipEditRequest(endMs = 80_000)) }
        assertEquals("SHORTS_CLIP_NOT_EDITABLE", ex.code)
    }

    @Test
    fun `원본 길이를 넘는 구간은 거절한다`() {
        val ex = assertFailsWith<BusinessException> { useCase.edit(7, 10, 1, 3, ClipEditRequest(endMs = 600_001)) }
        assertEquals("SHORTS_CLIP_EDIT_INVALID", ex.code)
    }

    @Test
    fun `렌더 전 클립은 지시서 없이 구간만 바꾸고 상태를 유지한다`() {
        every { clipRepository.findById(3) } returns renderedClip.copy(
            renderSpec = null, status = ClipStatus.HOOK_SELECTED, renderedVideoId = null,
        )
        every { renderJobRepository.findByRunAndClip(1, 3) } returns null

        useCase.edit(7, 10, 1, 3, ClipEditRequest(startMs = 62_000))

        assertEquals(ClipStatus.HOOK_SELECTED, saved.captured.status)
        assertNull(saved.captured.renderSpec)
        verify(exactly = 0) { renderJobRepository.deleteFinished(any(), any()) }
    }

    @Test
    fun `다른 워크스페이스의 실행·클립은 찾을 수 없다고 답한다`() {
        every { runRepository.findById(1) } returns run.copy(workspaceId = 99)
        assertFailsWith<NotFoundException> { useCase.edit(7, 10, 1, 3, ClipEditRequest(title = "x")) }

        setUp()
        every { clipRepository.findById(3) } returns renderedClip.copy(runId = 2)
        assertFailsWith<NotFoundException> { useCase.getEditor(7, 10, 1, 3) }

        setUp()
        every { workspaceRepository.findAccessibleByUserId(7) } returns emptyList()
        assertFailsWith<NotFoundException> { useCase.getEditor(7, 10, 1, 3) }
    }

    @Test
    fun `편집기 데이터는 단어 시각 유무와 플랫폼 메타를 함께 준다`() {
        every { clipRepository.findById(3) } returns renderedClip.copy(
            platformMetaJson = ShortsPlatformMeta.toJson(ShortsPlatformMeta.initial("제목", "설명", listOf("태그"))),
        )

        val editor = useCase.getEditor(7, 10, 1, 3)

        assertEquals(listOf(true, false), editor.subtitles.map { it.hasWordTiming })
        assertEquals(listOf("YOUTUBE", "TIKTOK", "INSTAGRAM"), editor.platformMeta.map { it.platform })
        assertEquals(600_000, editor.sourceDurationMs)
        assertTrue(editor.editable)
        assertTrue(editor.hasRenderedVideo)
    }
}
