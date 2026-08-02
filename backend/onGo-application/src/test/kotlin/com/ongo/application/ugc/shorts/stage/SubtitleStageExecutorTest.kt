package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipStatus
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
 * SubtitleStageExecutor — 전사 세그먼트의 클립 기준 상대 ms 변환과 AI 다듬기 적용 규칙 검증.
 */
@ExtendWith(MockKExtension::class)
class SubtitleStageExecutorTest {

    @MockK
    lateinit var chatClientResolver: ChatClientResolver

    @MockK
    lateinit var shortsPromptRepository: ShortsPromptRepository

    @InjectMockKs
    lateinit var executor: SubtitleStageExecutor

    private val mapper = jacksonObjectMapper()

    // 클립 구간: 10000ms ~ 20000ms
    private val clip = ShortsClip(id = 11, runId = 1, seq = 1, startMs = 10000, endMs = 20000, status = ClipStatus.DRAFT)

    // 8000~12000(클립 시작 전에 걸침), 12000~18000(온전히 포함), 25000~26000(범위 밖)
    private val segments = listOf(
        TranscriptSegmentMs(8000, 12000, "앞부분"),
        TranscriptSegmentMs(12000, 18000, "중간"),
        TranscriptSegmentMs(25000, 26000, "범위 밖"),
    )

    @BeforeEach
    fun setUp() {
        every { shortsPromptRepository.findByWorkspaceAndStage(any(), any()) } returns null
        every { shortsPromptRepository.findDefaultByStage(any()) } returns null
    }

    private fun subtitleLines(output: ShortsStageOutput, clipId: Long) =
        mapper.readTree(output.subtitles!!.getValue(clipId)).map {
            Triple(it.path("startMs").asLong(), it.path("endMs").asLong(), it.path("text").asText())
        }

    @Test
    fun `클립이 없으면 SHORTS_RUN_INVALID_STATE`() {
        val ex = assertFailsWith<BusinessException> {
            executor.execute(stageContext(clips = emptyList(), transcriptSegments = segments))
        }
        assertEquals("SHORTS_RUN_INVALID_STATE", ex.code)
    }

    @Test
    fun `전사 세그먼트를 클립 기준 상대 ms로 변환하고 범위 밖은 버린다`() {
        // 줄 수가 초안과 같으면 AI 문구를 쓴다
        stubChatClientEntity(
            chatClientResolver,
            SubtitlePolishResult::class.java,
            SubtitlePolishResult(clips = listOf(SubtitlePolishResult.ClipSubtitles(clipSeq = 1, lines = listOf("다듬은 앞부분", "다듬은 중간")))),
        )

        val output = executor.execute(stageContext(clips = listOf(clip), transcriptSegments = segments))

        // 8000~12000 → 클립 시작으로 잘려 0~2000, 12000~18000 → 2000~8000, 범위 밖 세그먼트는 제외
        assertEquals(
            listOf(Triple(0L, 2000L, "다듬은 앞부분"), Triple(2000L, 8000L, "다듬은 중간")),
            subtitleLines(output, 11L),
        )
    }

    @Test
    fun `AI 응답 줄 수가 초안과 다르면 초안 문구를 유지한다`() {
        stubChatClientEntity(
            chatClientResolver,
            SubtitlePolishResult::class.java,
            SubtitlePolishResult(clips = listOf(SubtitlePolishResult.ClipSubtitles(clipSeq = 1, lines = listOf("한 줄뿐")))),
        )

        val output = executor.execute(stageContext(clips = listOf(clip), transcriptSegments = segments))

        assertEquals(
            listOf(Triple(0L, 2000L, "앞부분"), Triple(2000L, 8000L, "중간")),
            subtitleLines(output, 11L),
        )
    }

    @Test
    fun `AI가 준 공백 줄은 초안 문구로 대체한다`() {
        stubChatClientEntity(
            chatClientResolver,
            SubtitlePolishResult::class.java,
            SubtitlePolishResult(clips = listOf(SubtitlePolishResult.ClipSubtitles(clipSeq = 1, lines = listOf(" ", "다듬은 중간")))),
        )

        val output = executor.execute(stageContext(clips = listOf(clip), transcriptSegments = segments))

        assertEquals(
            listOf(Triple(0L, 2000L, "앞부분"), Triple(2000L, 8000L, "다듬은 중간")),
            subtitleLines(output, 11L),
        )
    }

    @Test
    fun `클립 구간과 겹치는 세그먼트가 없으면 빈 자막 배열을 만든다`() {
        stubChatClientEntity(
            chatClientResolver,
            SubtitlePolishResult::class.java,
            SubtitlePolishResult(clips = listOf(SubtitlePolishResult.ClipSubtitles(clipSeq = 1, lines = emptyList()))),
        )

        val output = executor.execute(stageContext(clips = listOf(clip), transcriptSegments = emptyList()))

        assertEquals(emptyList(), subtitleLines(output, 11L))
    }
}
