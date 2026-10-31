package com.ongo.application.ugc.shorts.stage

import com.ongo.application.ai.ChatClientResolver
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ResponseEntity
import org.springframework.ai.chat.model.ChatResponse
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SegmentStageExecutor — AI 클립 후보 추출 결과의 필터링 규칙 검증.
 */
@ExtendWith(MockKExtension::class)
class SegmentStageExecutorTest {

    @MockK
    lateinit var chatClientResolver: ChatClientResolver

    @MockK
    lateinit var shortsPromptRepository: ShortsPromptRepository

    @InjectMockKs
    lateinit var executor: SegmentStageExecutor

    @BeforeEach
    fun setUp() {
        every { shortsPromptRepository.findByWorkspaceAndStage(any(), any()) } returns null
        every { shortsPromptRepository.findDefaultByStage(any()) } returns null
    }

    @Test
    fun `전사 결과가 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> {
            executor.execute(stageContext(transcriptText = null))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `끝이 시작보다 빠르거나 같은 후보는 버린다`() {
        stubChatClientEntity(
            chatClientResolver,
            SegmentExtractionResult::class.java,
            SegmentExtractionResult(
                clips = listOf(
                    SegmentExtractionResult.SegmentClip(title = "정상", caption = "캡션", startMs = 0, endMs = 15000),
                    SegmentExtractionResult.SegmentClip(title = "역전", caption = null, startMs = 20000, endMs = 10000),
                    SegmentExtractionResult.SegmentClip(title = "제로", caption = null, startMs = 5000, endMs = 5000),
                ),
            ),
        )

        val output = executor.execute(stageContext())

        assertEquals(1, output.clipCandidates!!.size)
        assertEquals("정상", output.clipCandidates!![0].title)
        assertEquals(0, output.clipCandidates!![0].startMs)
        assertEquals(15000, output.clipCandidates!![0].endMs)
    }

    @Test
    fun `유효한 후보가 하나도 없으면 AI_PARSE_ERROR`() {
        stubChatClientEntity(
            chatClientResolver,
            SegmentExtractionResult::class.java,
            SegmentExtractionResult(clips = listOf(SegmentExtractionResult.SegmentClip(startMs = 9000, endMs = 1000))),
        )

        val ex = assertFailsWith<BusinessException> { executor.execute(stageContext()) }
        assertEquals("AI_PARSE_ERROR", ex.code)
    }

    @Test
    fun `AI 응답을 파싱하지 못하면 AI_PARSE_ERROR`() {
        // entity가 null을 반환하는 경우
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(any()) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.responseEntity(SegmentExtractionResult::class.java) } returns
            ResponseEntity(mockk<ChatResponse>(), null)

        val ex = assertFailsWith<BusinessException> { executor.execute(stageContext()) }
        assertEquals("AI_PARSE_ERROR", ex.code)
    }

    @Test
    fun `정상 추출 시 후보와 입력 스냅샷을 반환하고 프롬프트 id는 폴팩이라 기록하지 않는다`() {
        stubChatClientEntity(
            chatClientResolver,
            SegmentExtractionResult::class.java,
            SegmentExtractionResult(
                clips = listOf(
                    SegmentExtractionResult.SegmentClip(title = "제목1", caption = "캡션1", startMs = 0, endMs = 15000),
                    SegmentExtractionResult.SegmentClip(title = "제목2", caption = "캡션2", startMs = 15000, endMs = 30000),
                ),
            ),
        )

        val output = executor.execute(
            // 문장 경계 보정이 있으므로 전사 세그먼트가 두 후보 구간을 실제처럼 덮어야 한다.
            stageContext(
                transcriptSegments = listOf(
                    TranscriptSegmentMs(0, 5000, "안녕"),
                    TranscriptSegmentMs(5000, 15000, "반가워"),
                    TranscriptSegmentMs(15000, 30000, "끝"),
                ),
            ),
        )

        assertEquals(2, output.clipCandidates!!.size)
        assertNull(output.promptId) // 폴팩 프롬프트(id=0)는 기록하지 않는다
        // "전사 전문"은 5글자, 세그먼트 3개. 조건을 정하지 않았으므로 서버 기본값이 기록된다.
        assertEquals(
            """{"transcriptLength":5,"segmentCount":3,"clipCount":5,"minDurationMs":15000,"maxDurationMs":90000}""",
            output.inputSnapshot,
        )
    }

    @Test
    fun `AI 점수와 이유를 후보에 싣고 점수 높은 순으로 돌려준다`() {
        stubChatClientEntity(
            chatClientResolver,
            SegmentExtractionResult::class.java,
            SegmentExtractionResult(
                clips = listOf(
                    SegmentExtractionResult.SegmentClip(title = "중", startMs = 0, endMs = 20_000, score = 55, reason = "  결론이 약함 "),
                    SegmentExtractionResult.SegmentClip(title = "상", startMs = 30_000, endMs = 50_000, score = 92, reason = "첫 문장이 질문"),
                    SegmentExtractionResult.SegmentClip(title = "무", startMs = 60_000, endMs = 80_000, score = null, reason = ""),
                ),
            ),
        )

        val clips = executor.execute(stageContext()).clipCandidates!!

        assertEquals(listOf("상", "중", "무"), clips.map { it.title })
        assertEquals(listOf(92, 55, null), clips.map { it.score })
        assertEquals(listOf("첫 문장이 질문", "결론이 약함", null), clips.map { it.scoreReason })
    }

    @Test
    fun `실행에 저장된 개수·길이 조건을 프롬프트에 넣고 결과에도 적용한다`() {
        val userPrompt = slot<String>()
        stubCapturingUserPrompt(
            userPrompt,
            SegmentExtractionResult(
                clips = listOf(
                    SegmentExtractionResult.SegmentClip(title = "짧음", startMs = 0, endMs = 10_000, score = 99),
                    SegmentExtractionResult.SegmentClip(title = "a", startMs = 20_000, endMs = 50_000, score = 80),
                    SegmentExtractionResult.SegmentClip(title = "b", startMs = 60_000, endMs = 90_000, score = 70),
                ),
            ),
        )
        val context = stageContext().apply {
            run = run.copy(segmentClipCount = 1, segmentMinDurationMs = 25_000, segmentMaxDurationMs = 40_000)
        }

        val clips = executor.execute(context).clipCandidates!!

        // 10초짜리는 최소 25초 미달로 버리고, 남은 둘 중 점수 높은 하나만
        assertEquals(listOf("a"), clips.map { it.title })
        assertTrue(userPrompt.captured.contains("클립은 최대 3개"))
        assertTrue(userPrompt.captured.contains("25초 이상 40초 이하"))
        assertTrue(userPrompt.captured.contains("score"))
    }

    @Test
    fun `워크스페이스가 프롬프트를 바꿔도 점수 형식과 조건은 항상 붙는다`() {
        every { shortsPromptRepository.findByWorkspaceAndStage(any(), any()) } returns
            com.ongo.application.ugc.shorts.ShortsPromptDefaults.fallback(com.ongo.domain.ugc.shorts.PipelineStage.SEGMENT)
                .copy(userPrompt = "아무 구간이나 골라 줘")
        val userPrompt = slot<String>()
        stubCapturingUserPrompt(
            userPrompt,
            SegmentExtractionResult(clips = listOf(SegmentExtractionResult.SegmentClip(startMs = 0, endMs = 20_000, score = 50))),
        )

        executor.execute(stageContext())

        assertTrue(userPrompt.captured.startsWith("아무 구간이나 골라 줘"))
        assertTrue(userPrompt.captured.contains("{title, caption, startMs, endMs, score, reason, hashtags}"))
    }

    @Test
    fun `조건에 맞는 구간이 하나도 없으면 SHORTS_SEGMENT_NO_FIT 으로 실패한다`() {
        stubChatClientEntity(
            chatClientResolver,
            SegmentExtractionResult::class.java,
            SegmentExtractionResult(clips = listOf(SegmentExtractionResult.SegmentClip(startMs = 0, endMs = 3_000, score = 90))),
        )

        val ex = assertFailsWith<BusinessException> { executor.execute(stageContext()) }
        assertEquals("SHORTS_SEGMENT_NO_FIT", ex.code)
    }

    @Test
    fun `AI 경계가 문장 중간이면 전사 세그먼트 경계로 맞춘다`() {
        stubChatClientEntity(
            chatClientResolver,
            SegmentExtractionResult::class.java,
            SegmentExtractionResult(clips = listOf(SegmentExtractionResult.SegmentClip(startMs = 4_200, endMs = 23_900, score = 70))),
        )
        val segments = listOf(
            TranscriptSegmentMs(0, 8_000, "첫 문장"),
            TranscriptSegmentMs(8_000, 16_000, "둘째 문장"),
            TranscriptSegmentMs(16_000, 26_000, "셋째 문장"),
        )

        val clip = executor.execute(stageContext(transcriptSegments = segments)).clipCandidates!!.single()

        assertEquals(0, clip.startMs)
        assertEquals(26_000, clip.endMs)
    }

    private fun stubCapturingUserPrompt(captured: io.mockk.CapturingSlot<String>, result: SegmentExtractionResult) {
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>()
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val chatClient = mockk<ChatClient>()
        every { chatClientResolver.resolve(any()) } returns chatClient
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.system(any<String>()) } returns requestSpec
        every { requestSpec.user(capture(captured)) } returns requestSpec
        every { requestSpec.call() } returns callSpec
        every { callSpec.responseEntity(SegmentExtractionResult::class.java) } returns
            ResponseEntity(mockk<ChatResponse>(), result)
    }
}
