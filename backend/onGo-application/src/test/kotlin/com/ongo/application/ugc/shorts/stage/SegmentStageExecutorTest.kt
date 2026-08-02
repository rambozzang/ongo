package com.ongo.application.ugc.shorts.stage

import com.ongo.application.ai.ChatClientResolver
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.ai.chat.client.ChatClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
        every { callSpec.entity(SegmentExtractionResult::class.java) } returns null

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
            stageContext(transcriptSegments = listOf(TranscriptSegmentMs(0, 5000, "안녕"))),
        )

        assertEquals(2, output.clipCandidates!!.size)
        assertNull(output.promptId) // 폴팩 프롬프트(id=0)는 기록하지 않는다
        // "전사 전문"은 5글자, 세그먼트 1개
        assertEquals("""{"transcriptLength":5,"segmentCount":1}""", output.inputSnapshot)
    }
}
