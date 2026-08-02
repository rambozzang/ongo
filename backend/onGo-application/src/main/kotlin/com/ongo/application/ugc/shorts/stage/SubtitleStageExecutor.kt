package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * SUBTITLE 단계 (AI). 클립 구간의 STT 세그먼트를 클립 기준 상대 ms 자막으로 만들고,
 * AI로 문구를 다듬는다 (개수·순서가 맞을 때만 AI 문구를 쓰고, 아니면 원문 유지).
 */
@Component
class SubtitleStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.SUBTITLE

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        if (context.clips.isEmpty()) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "클립이 없어 자막을 만들 수 없습니다")
        }

        // 클립별 상대 ms 자막 초안 [{startMs, endMs, text}]
        val drafts = context.clips.associate { clip ->
            clip.id to relativeLines(clip.startMs, clip.endMs, context.transcriptSegments)
        }

        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)

        val draftDescription = context.clips.joinToString("\n") { clip ->
            val lines = drafts.getValue(clip.id).mapIndexed { i, line -> "${i + 1}. ${line.text}" }
            "클립 ${clip.seq}:\n$lines"
        }

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n클립별 자막 초안:\n").append(InputSanitizer.sanitize(draftDescription.take(8000)))
            append("\n\n각 클립의 자막 줄을 다듬어 줘. 줄 개수와 순서는 입력과 반드시 같아야 해. 응답은 clips 배열에 {clipSeq, lines} 객체를 담은 JSON으로만 해 줘.")
        }

        val polished = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(SubtitlePolishResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "자막 응답을 파싱할 수 없습니다")

        val polishedBySeq = polished.clips.associateBy { it.clipSeq }

        val subtitles = context.clips.associate { clip ->
            val draft = drafts.getValue(clip.id)
            val aiLines = polishedBySeq[clip.seq]?.lines
            // 개수·순서가 맞을 때만 AI 문구를 쓰고, 아니면 초안 원문을 유지한다
            val lines = if (aiLines != null && aiLines.size == draft.size) {
                draft.mapIndexed { i, line -> line.copy(text = aiLines[i].ifBlank { line.text }) }
            } else {
                draft
            }
            clip.id to mapper.writeValueAsString(
                lines.map { mapOf("startMs" to it.startMs, "endMs" to it.endMs, "text" to it.text) },
            )
        }

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(
                mapOf("clips" to context.clips.map { mapOf("clipId" to it.id, "subtitleCount" to drafts.getValue(it.id).size) }),
            ),
            inputSnapshot = """{"clipCount":${context.clips.size}}""",
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
            subtitles = subtitles,
        )
    }

    /** 클립 구간과 겹치는 전사 세그먼트를 클립 기준 상대 ms로 변환한다. */
    private fun relativeLines(
        clipStartMs: Long,
        clipEndMs: Long,
        segments: List<TranscriptSegmentMs>,
    ): List<TranscriptSegmentMs> =
        segments
            .filter { it.endMs > clipStartMs && it.startMs < clipEndMs }
            .map { segment ->
                TranscriptSegmentMs(
                    startMs = (segment.startMs - clipStartMs).coerceAtLeast(0),
                    endMs = (minOf(segment.endMs, clipEndMs) - clipStartMs),
                    text = segment.text.trim(),
                )
            }
            .filter { it.endMs > it.startMs && it.text.isNotBlank() }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 쇼츠 자막 편집자입니다. 자막은 맥락 위주로 끊되 한 줄이 다섯 자에서 아홉 자 사이가 되게 맞춥니다."
    }
}
