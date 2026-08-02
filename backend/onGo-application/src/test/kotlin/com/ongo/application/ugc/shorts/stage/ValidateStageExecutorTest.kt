package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ValidateStageExecutor — 결정론적 검증 리포트 생성 규칙 검증.
 */
@ExtendWith(MockKExtension::class)
class ValidateStageExecutorTest {

    @MockK
    lateinit var chatClientResolver: ChatClientResolver

    @MockK
    lateinit var shortsPromptRepository: ShortsPromptRepository

    @InjectMockKs
    lateinit var executor: ValidateStageExecutor

    private val mapper = jacksonObjectMapper()

    private fun clip(
        id: Long,
        seq: Int,
        status: ClipStatus = ClipStatus.RENDER_READY,
        subtitleJson: String? = """[{"startMs":0,"endMs":1500,"text":"자막"}]""",
        renderSpec: String? = "{}",
    ) = ShortsClip(
        id = id, runId = 1, seq = seq,
        startMs = (seq - 1) * 15000L, endMs = seq * 15000L,
        status = status, subtitleJson = subtitleJson, renderSpec = renderSpec,
    )

    @BeforeEach
    fun setUp() {
        // 프롬프트는 오버라이드/기본값 모두 없어서 폴팩 상수로 떨어진다
        every { shortsPromptRepository.findByWorkspaceAndStage(any(), any()) } returns null
        every { shortsPromptRepository.findDefaultByStage(any()) } returns null
    }

    @Test
    fun `검증 대상 클립이 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> {
            executor.execute(stageContext(clips = listOf(clip(11, 1, ClipStatus.DISCARDED))))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `자막 미생성, 후킹 미선택, 렌더 스펙 없음이 issues에 기록된다`() {
        stubChatClientEntity(chatClientResolver, ValidateVerdictResult::class.java, ValidateVerdictResult(false, "미흡합니다"))
        val broken = clip(11, 1, subtitleJson = null, renderSpec = null)

        val output = executor.execute(stageContext(clips = listOf(broken), hooks = emptyMap()))

        val report = mapper.readTree(output.outputSnapshot).path("clips")[0]
        val issues = report.path("issues").map { it.asText() }
        assertEquals(3, issues.size)
        assertTrue(issues.contains("자막이 없습니다"))
        assertTrue(issues.contains("후킹 문구가 선택되지 않았습니다"))
        assertTrue(issues.contains("렌더 스펙이 없습니다"))
        assertEquals(0, report.path("subtitleCount").asInt())
        assertTrue(!report.path("hookSelected").asBoolean())
        assertTrue(!report.path("hasRenderSpec").asBoolean())
    }

    @Test
    fun `구간이 0 이하인 클립은 구간 오류가 issues에 기록된다`() {
        stubChatClientEntity(chatClientResolver, ValidateVerdictResult::class.java, ValidateVerdictResult(false, "미흡합니다"))
        val inverted = ShortsClip(
            id = 11, runId = 1, seq = 1, startMs = 15000, endMs = 15000,
            subtitleJson = """[{"startMs":0,"endMs":1,"text":"자"}]""", renderSpec = "{}",
        )
        val hooks = mapOf(11L to listOf(ClipHook(id = 21, clipId = 11, variant = HookVariant.A, text = "A안", selected = true)))

        val output = executor.execute(stageContext(clips = listOf(inverted), hooks = hooks))

        val issues = mapper.readTree(output.outputSnapshot).path("clips")[0].path("issues").map { it.asText() }
        assertEquals(listOf("클립 구간이 올바르지 않습니다"), issues)
    }

    @Test
    fun `모든 항목이 갖춰지면 issues가 비고 AI 총평이 스냅샷에 담긴다`() {
        stubChatClientEntity(chatClientResolver, ValidateVerdictResult::class.java, ValidateVerdictResult(true, "게시 가능"))
        val ready = clip(11, 1)
        val hooks = mapOf(11L to listOf(ClipHook(id = 21, clipId = 11, variant = HookVariant.B, text = "B안", selected = true)))

        val output = executor.execute(stageContext(clips = listOf(ready), hooks = hooks))

        val snapshot = mapper.readTree(output.outputSnapshot)
        val report = snapshot.path("clips")[0]
        assertEquals(0, report.path("issues").size())
        assertEquals(1, report.path("subtitleCount").asInt())
        assertTrue(report.path("hookSelected").asBoolean())
        assertTrue(report.path("hasRenderSpec").asBoolean())
        assertTrue(snapshot.path("passed").asBoolean())
        assertEquals("게시 가능", snapshot.path("summary").asText())
    }

    @Test
    fun `DISCARDED 클립은 검증 리포트에서 제외된다`() {
        stubChatClientEntity(chatClientResolver, ValidateVerdictResult::class.java, ValidateVerdictResult(true, "ok"))
        val clips = listOf(clip(11, 1), clip(12, 2, ClipStatus.DISCARDED))
        val hooks = mapOf(11L to listOf(ClipHook(id = 21, clipId = 11, variant = HookVariant.A, text = "A안", selected = true)))

        val output = executor.execute(stageContext(clips = clips, hooks = hooks))

        val report = mapper.readTree(output.outputSnapshot).path("clips")
        assertEquals(1, report.size())
        assertEquals(11, report[0].path("clipId").asLong())
    }
}
