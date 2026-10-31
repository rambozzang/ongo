package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.application.ugc.shorts.ShortsCostLedger
import com.ongo.application.ugc.shorts.ShortsPlatformMeta
import com.ongo.application.ugc.shorts.ShortsSegmentPolicy
import com.ongo.application.ugc.shorts.segmentOptions
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * SEGMENT 단계 (AI). 전사+세그먼트를 클립 후보 [{title,caption,startMs,endMs,score,reason}] 로 뽑는다.
 * AI 제안은 [ShortsSegmentPolicy] 가 문장 경계·길이·중복·개수 규칙으로 다듬고, 점수 높은 순으로 돌려준다.
 * 실제 ShortsClip 저장과 clipCount 갱신은 오케스트레이터가 한다.
 */
@Component
class SegmentStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
    private val costLedger: ShortsCostLedger? = null,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.SEGMENT

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val transcript = context.transcriptText
            ?: throw BusinessException("SHORTS_RUN_INVALID_STATE", "전사 결과가 없어 맥락 컷을 진행할 수 없습니다")

        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)
        val options = context.run.segmentOptions()

        val segmentLines = context.transcriptSegments.joinToString("\n") {
            "[${it.startMs}ms ~ ${it.endMs}ms] ${it.text}"
        }

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n전사 전문: ").append(InputSanitizer.sanitize(transcript))
            if (segmentLines.isNotBlank()) {
                append("\n\n타임코드 세그먼트:\n").append(InputSanitizer.sanitize(segmentLines.take(8000)))
            }
            // 조건과 응답 형식은 워크스페이스가 프롬프트를 바꿔도 항상 붙인다. 점수가 빠지면 정렬할 수 없다.
            append("\n\n")
            append(selectionRules(options, hasSegments = segmentLines.isNotBlank()))
        }

        val response = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .responseEntity(SegmentExtractionResult::class.java)
        costLedger?.recordLlm(context.run.id, context.userId, stage.name, response.response)
        val result = response.entity
            ?: throw BusinessException("AI_PARSE_ERROR", "클립 후보 응답을 파싱할 수 없습니다")

        // 유효하지 않은 구간(end <= start)은 버린다
        val candidates = result.clips
            .filter { it.endMs > it.startMs }
            .map {
                ClipCandidate(
                    title = it.title,
                    caption = it.caption,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    score = it.score,
                    scoreReason = it.reason?.trim()?.takeIf(String::isNotEmpty),
                    hashtags = it.hashtags,
                )
            }

        if (candidates.isEmpty()) {
            throw BusinessException("AI_PARSE_ERROR", "AI가 유효한 클립 후보를 만들지 못했습니다")
        }

        val selection = ShortsSegmentPolicy.select(candidates, context.transcriptSegments, options)
        if (selection.clips.isEmpty()) {
            throw BusinessException(
                "SHORTS_SEGMENT_NO_FIT",
                "${options.effectiveMinMs / 1000}~${options.effectiveMaxMs / 1000}초 길이로 문장이 끊기지 않는 구간을 찾지 못했습니다. " +
                    "길이 범위를 넓혀 다시 시도해 주세요",
            )
        }

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(
                mapOf(
                    "clips" to selection.clips,
                    "proposedCount" to candidates.size,
                    "droppedForLength" to selection.droppedForLength,
                    "droppedForOverlap" to selection.droppedForOverlap,
                    "droppedForCount" to selection.droppedForCount,
                ),
            ),
            inputSnapshot = mapper.writeValueAsString(
                mapOf(
                    "transcriptLength" to transcript.length,
                    "segmentCount" to context.transcriptSegments.size,
                    "clipCount" to options.effectiveClipCount,
                    "minDurationMs" to options.effectiveMinMs,
                    "maxDurationMs" to options.effectiveMaxMs,
                ),
            ),
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
            clipCandidates = selection.clips,
        )
    }

    /**
     * 선택 조건·점수 기준·응답 형식. 점수 기준은 시스템 프롬프트의 단 하나의 판단 기준
     * ("롱폼을 보지 않은 사람이 이 클립만 보고 이해하는가")을 그대로 잘게 나눈 것이다 —
     * "반응이 좋을 것 같은 구간"을 따로 점수로 매기게 하면 시스템 지시와 충돌한다.
     */
    private fun selectionRules(options: ShortsSegmentPolicy.Options, hasSegments: Boolean): String = buildString {
        append("[선택 조건 — 위 지시와 다르면 이 조건을 따르세요]\n")
        append("- 클립은 최대 ${options.requestCount}개.\n")
        append("- 각 클립 길이는 ${options.effectiveMinMs / 1000}초 이상 ${options.effectiveMaxMs / 1000}초 이하.\n")
        if (hasSegments) {
            append("- startMs 는 타임코드 세그먼트의 시작값, endMs 는 끝값 중에서 고르세요. 문장 중간에서 시작하거나 끝내지 마세요.\n")
        }
        append("- 서로 같은 장면을 겹쳐 고르지 마세요.\n")
        append("\n[점수 기준] score 는 0~100 정수. 롱폼을 보지 않은 사람이 이 클립만 보고 이해하고 끝까지 볼지를 매깁니다.\n")
        append("- 첫 3초 안에 무엇에 관한 이야기인지, 왜 들어야 하는지가 드러나는가\n")
        append("- 클립 안에서 이야기가 완결되는가 (질문만 있고 답이 잘리면 크게 감점)\n")
        append("- 앞 맥락을 몰라도 되는가 (\"아까 말한\", \"그거\" 같은 지시어에 기대면 감점)\n")
        append("reason 은 그 점수의 근거를 한국어 한 문장(50자 이내)으로 쓰세요.\n")
        append("hashtags 는 이 클립 내용에 맞는 해시태그 ${ShortsPlatformMeta.MAX_HASHTAGS}개 이하(# 없이, 띄어쓰기 없이)로 쓰세요.\n")
        append("\n응답은 clips 배열에 {title, caption, startMs, endMs, score, reason, hashtags} 객체를 담은 JSON으로만 해 줘. ")
        append("title 은 40자, caption 은 100자 이내.")
    }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 롱폼 전사에서 쇼츠 클립을 고르는 편집자입니다. 롱폼을 보지 않은 사람이 그 클립만 보고도 이해할 수 있는 구간만 고릅니다."
    }
}
