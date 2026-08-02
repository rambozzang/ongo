package com.ongo.domain.ugc.shorts

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineStageTest {

    @Test
    fun `파이프라인은 9단계로 구성된다`() {
        assertEquals(9, PipelineStage.entries.size)
    }

    @Test
    fun `sortOrder는 1부터 9까지 중복 없이 부여된다`() {
        val orders = PipelineStage.entries.map { it.sortOrder }
        assertEquals((1..9).toList(), orders.sorted())
    }

    @Test
    fun `단계 순서는 설계 문서의 9단계와 일치한다`() {
        val ordered = PipelineStage.entries.sortedBy { it.sortOrder }
        assertEquals(
            listOf(
                PipelineStage.TRANSCRIBE,
                PipelineStage.REFRAME,
                PipelineStage.SEGMENT,
                PipelineStage.SUBTITLE,
                PipelineStage.HOOK,
                PipelineStage.TEMPLATE,
                PipelineStage.RENDER_SPEC,
                PipelineStage.VALIDATE,
                PipelineStage.SCHEDULE,
            ),
            ordered,
        )
    }

    @Test
    fun `displayName이 단계별로 부여된다`() {
        assertEquals("전사", PipelineStage.TRANSCRIBE.displayName)
        assertEquals("세로 변환", PipelineStage.REFRAME.displayName)
        assertEquals("맥락 컷", PipelineStage.SEGMENT.displayName)
        assertEquals("자막", PipelineStage.SUBTITLE.displayName)
        assertEquals("후킹 문구", PipelineStage.HOOK.displayName)
        assertEquals("템플릿", PipelineStage.TEMPLATE.displayName)
        assertEquals("렌더 스펙", PipelineStage.RENDER_SPEC.displayName)
        assertEquals("검증", PipelineStage.VALIDATE.displayName)
        assertEquals("예약", PipelineStage.SCHEDULE.displayName)
    }

    @Test
    fun `AI 실행 여부는 설계 문서 5_2절 표와 일치한다`() {
        val executable = PipelineStage.entries.filter { it.aiExecutable }.toSet()
        assertEquals(
            setOf(
                PipelineStage.REFRAME,
                PipelineStage.SEGMENT,
                PipelineStage.SUBTITLE,
                PipelineStage.HOOK,
                PipelineStage.TEMPLATE,
                PipelineStage.VALIDATE,
            ),
            executable,
        )
        val notExecutable = PipelineStage.entries.filter { !it.aiExecutable }.toSet()
        assertEquals(
            setOf(PipelineStage.TRANSCRIBE, PipelineStage.RENDER_SPEC, PipelineStage.SCHEDULE),
            notExecutable,
        )
    }

    @Test
    fun `개정 증가 규칙 - 오버라이드 copy로 revision을 1 올릴 수 있다`() {
        val prompt = ShortsPrompt(
            id = 1,
            workspaceId = 10,
            stage = PipelineStage.SEGMENT,
            name = "맥락 컷",
            userPrompt = "원문",
            revision = 2,
        )
        val bumped = prompt.copy(userPrompt = "수정", revision = prompt.revision + 1)
        assertEquals(3, bumped.revision)
        assertEquals(2, prompt.revision) // 원본은 불변
        assertTrue(bumped.userPrompt == "수정")
    }
}
