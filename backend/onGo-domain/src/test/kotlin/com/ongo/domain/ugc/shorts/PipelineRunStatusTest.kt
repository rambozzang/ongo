package com.ongo.domain.ugc.shorts

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PipelineRunStatusTest {

    @Test
    fun `실행 상태는 7개로 구성된다`() {
        assertEquals(7, PipelineRunStatus.entries.size)
    }

    @Test
    fun `사람의 입력을 기다리는 게이트 상태가 2개 정의되어 있다`() {
        // Phase 2 상태 전이의 핵심: HOOK 뒤와 VALIDATE 뒤에 멈추는 게이트
        val gates = PipelineRunStatus.entries.filter { it.name.startsWith("AWAITING_") }.toSet()
        assertEquals(
            setOf(PipelineRunStatus.AWAITING_HOOK_SELECTION, PipelineRunStatus.AWAITING_SCHEDULE),
            gates,
        )
    }

    @Test
    fun `상태 전이의 기점과 종착지 상태가 모두 존재한다`() {
        // PENDING → RUNNING → (게이트) → COMPLETED, 실패/취소는 FAILED/CANCELLED
        val all = PipelineRunStatus.entries.toSet()
        assertTrue(
            all.containsAll(
                listOf(
                    PipelineRunStatus.PENDING,
                    PipelineRunStatus.RUNNING,
                    PipelineRunStatus.COMPLETED,
                    PipelineRunStatus.FAILED,
                    PipelineRunStatus.CANCELLED,
                ),
            ),
        )
    }
}
