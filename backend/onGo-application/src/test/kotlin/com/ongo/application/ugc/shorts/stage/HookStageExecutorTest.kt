package com.ongo.application.ugc.shorts.stage

import com.ongo.application.ai.ChatClientResolver
import com.ongo.common.exception.BusinessException
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

/**
 * HookStageExecutor — 클립별 후킹 문구 A/B 생성 매핑 규칙 검증.
 */
@ExtendWith(MockKExtension::class)
class HookStageExecutorTest {

    @MockK
    lateinit var chatClientResolver: ChatClientResolver

    @MockK
    lateinit var shortsPromptRepository: ShortsPromptRepository

    @InjectMockKs
    lateinit var executor: HookStageExecutor

    private fun clip(id: Long, seq: Int) = ShortsClip(
        id = id, runId = 1, seq = seq,
        startMs = (seq - 1) * 15000L, endMs = seq * 15000L,
        title = "클립 $seq", caption = "캡션 $seq", status = ClipStatus.DRAFT,
    )

    @BeforeEach
    fun setUp() {
        every { shortsPromptRepository.findByWorkspaceAndStage(any(), any()) } returns null
        every { shortsPromptRepository.findDefaultByStage(any()) } returns null
    }

    @Test
    fun `클립이 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> { executor.execute(stageContext(clips = emptyList())) }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `클립마다 A안 B안 두 개의 후킹을 만들고 clipId로 매핑한다`() {
        stubChatClientEntity(
            chatClientResolver,
            HookGenerationResult::class.java,
            HookGenerationResult(
                clips = listOf(
                    HookGenerationResult.ClipHooks(clipSeq = 1, hookA = "A1안", hookB = "B1안"),
                    HookGenerationResult.ClipHooks(clipSeq = 2, hookA = "A2안", hookB = "B2안"),
                ),
            ),
        )

        val output = executor.execute(stageContext(clips = listOf(clip(11, 1), clip(12, 2))))

        assertEquals(4, output.hooks!!.size)
        val byClip = output.hooks!!.groupBy { it.clipId }
        assertEquals(
            listOf(HookVariant.A to "A1안", HookVariant.B to "B1안"),
            byClip.getValue(11L).map { it.variant to it.text },
        )
        assertEquals(
            listOf(HookVariant.A to "A2안", HookVariant.B to "B2안"),
            byClip.getValue(12L).map { it.variant to it.text },
        )
    }

    @Test
    fun `응답에 없는 클립이 있으면 AI_PARSE_ERROR`() {
        stubChatClientEntity(
            chatClientResolver,
            HookGenerationResult::class.java,
            HookGenerationResult(clips = listOf(HookGenerationResult.ClipHooks(clipSeq = 1, hookA = "A1안", hookB = "B1안"))),
        )

        val ex = assertFailsWith<BusinessException> {
            executor.execute(stageContext(clips = listOf(clip(11, 1), clip(12, 2))))
        }
        assertEquals("AI_PARSE_ERROR", ex.code)
    }
}
