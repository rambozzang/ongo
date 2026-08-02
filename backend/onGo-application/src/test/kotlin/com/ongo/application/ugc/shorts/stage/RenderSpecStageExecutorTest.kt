package com.ongo.application.ugc.shorts.stage

import com.ongo.application.ugc.shorts.ShortsRenderSpecBuilder
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsTemplate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RenderSpecStageExecutor — 클립별 render-spec.json 생성 규칙 검증.
 */
class RenderSpecStageExecutorTest {

    private val builder = ShortsRenderSpecBuilder()
    private val executor = RenderSpecStageExecutor(builder)

    private fun clip(
        id: Long,
        seq: Int,
        status: ClipStatus = ClipStatus.HOOK_SELECTED,
        subtitleJson: String? = """[{"startMs":0,"endMs":1500,"text":"자막"}]""",
        cropJson: String? = """{"x":10,"y":20,"width":1080,"height":1920}""",
    ) = ShortsClip(
        id = id, runId = 1, seq = seq,
        startMs = (seq - 1) * 15000L, endMs = seq * 15000L,
        status = status, subtitleJson = subtitleJson, cropJson = cropJson,
    )

    @Test
    fun `대상 클립이 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> {
            executor.execute(stageContext(clips = listOf(clip(11, 1, ClipStatus.DISCARDED))))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `선택된 후킹 문구가 렌더 스펙에 반영된다`() {
        val clips = listOf(clip(11, 1))
        val hooks = mapOf(
            11L to listOf(
                ClipHook(id = 21, clipId = 11, variant = HookVariant.A, text = "A안", selected = false),
                ClipHook(id = 22, clipId = 11, variant = HookVariant.B, text = "B안 선택됨", selected = true),
            ),
        )

        val output = executor.execute(stageContext(clips = clips, hooks = hooks))

        val spec = builder.parseSpec(output.renderSpecs!!.getValue(11L))
        assertEquals("B안 선택됨", spec.hookText)
        assertEquals(1, spec.clipSeq)
        assertEquals(5, spec.sourceVideoId)
        assertEquals("https://cdn.example.com/source.mp4", spec.sourceFileUrl)
        assertEquals(ShortsRenderSpecBuilder.CropBox(10, 20, 1080, 1920), spec.crop)
        assertEquals(listOf(ShortsRenderSpecBuilder.SubtitleLine(0, 1500, "자막")), spec.subtitles)
    }

    @Test
    fun `DISCARDED 클립은 렌더 스펙을 만들지 않는다`() {
        val clips = listOf(clip(11, 1), clip(12, 2, ClipStatus.DISCARDED))

        val output = executor.execute(stageContext(clips = clips))

        assertEquals(setOf(11L), output.renderSpecs!!.keys)
    }

    @Test
    fun `선택된 후킹이 없으면 hookText는 null이다`() {
        val clips = listOf(clip(11, 1))
        val hooks = mapOf(
            11L to listOf(ClipHook(id = 21, clipId = 11, variant = HookVariant.A, text = "A안", selected = false)),
        )

        val output = executor.execute(stageContext(clips = clips, hooks = hooks))

        assertNull(builder.parseSpec(output.renderSpecs!!.getValue(11L)).hookText)
    }

    @Test
    fun `템플릿이 있으면 후킹 위치와 서식이 렌더 스펙에 반영된다`() {
        val template = ShortsTemplate(
            id = 7, workspaceId = 10, name = "기본",
            hookPosition = "BOTTOM", backgroundStyle = "BLUR", captionFontFamily = "Noto Sans KR",
            createdBy = 1,
        )

        val output = executor.execute(stageContext(clips = listOf(clip(11, 1)), template = template))

        val spec = builder.parseSpec(output.renderSpecs!!.getValue(11L))
        assertEquals(7, spec.templateId)
        assertEquals("BOTTOM", spec.hookPosition)
        assertEquals("BLUR", spec.backgroundStyle)
        assertEquals("Noto Sans KR", spec.captionFontFamily)
    }

    @Test
    fun `출력 스냅샷에 대상 클립 목록이 기록된다`() {
        val output = executor.execute(stageContext(clips = listOf(clip(11, 1), clip(12, 2))))

        assertTrue(output.outputSnapshot.contains("\"clipId\":11"))
        assertTrue(output.outputSnapshot.contains("\"clipId\":12"))
        assertEquals("""{"clipCount":2}""", output.inputSnapshot)
    }
}
