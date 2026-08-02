package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * SEGMENT 단계 (AI). 전사+세그먼트를 클립 후보 [{title,caption,startMs,endMs}] 로 뽑는다.
 * 실제 ShortsClip 저장과 clipCount 갱신은 오케스트레이터가 한다.
 */
@Component
class SegmentStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.SEGMENT

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val transcript = context.transcriptText
            ?: throw BusinessException("SHORTS_RUN_INVALID_STATE", "전사 결과가 없어 맥락 컷을 진행할 수 없습니다")

        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)

        val segmentLines = context.transcriptSegments.joinToString("\n") {
            "[${it.startMs}ms ~ ${it.endMs}ms] ${it.text}"
        }

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n전사 전문: ").append(InputSanitizer.sanitize(transcript))
            if (segmentLines.isNotBlank()) {
                append("\n\n타임코드 세그먼트:\n").append(InputSanitizer.sanitize(segmentLines.take(8000)))
            }
            append("\n\n응답은 clips 배열에 {title, caption, startMs, endMs} 객체를 담은 JSON으로만 해 줘.")
        }

        val result = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(SegmentExtractionResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "클립 후보 응답을 파싱할 수 없습니다")

        // 유효하지 않은 구간(end <= start)은 버린다
        val candidates = result.clips
            .filter { it.endMs > it.startMs }
            .map { ClipCandidate(title = it.title, caption = it.caption, startMs = it.startMs, endMs = it.endMs) }

        if (candidates.isEmpty()) {
            throw BusinessException("AI_PARSE_ERROR", "AI가 유효한 클립 후보를 만들지 못했습니다")
        }

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(mapOf("clips" to candidates)),
            inputSnapshot = """{"transcriptLength":${transcript.length},"segmentCount":${context.transcriptSegments.size}}""",
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
            clipCandidates = candidates,
        )
    }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 롱폼 전사에서 쇼츠 클립을 고르는 편집자입니다. 롱폼을 보지 않은 사람이 그 클립만 보고도 이해할 수 있는 구간만 고릅니다."
    }
}
