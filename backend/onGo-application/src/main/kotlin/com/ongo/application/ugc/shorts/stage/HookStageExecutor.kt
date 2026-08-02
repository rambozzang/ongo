package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.HookVariant
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * HOOK 단계 (AI). 클립별 후킹 문구 A/B 2개를 만든다.
 * ClipHook 저장은 오케스트레이터가 한다.
 */
@Component
class HookStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.HOOK

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        if (context.clips.isEmpty()) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "클립이 없어 후킹 문구를 만들 수 없습니다")
        }

        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)

        val clipDescription = context.clips.joinToString("\n") { clip ->
            "클립 ${clip.seq}: 제목=${clip.title ?: ""}, 요약=${clip.caption ?: ""}, 구간=${clip.startMs}ms~${clip.endMs}ms"
        }

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n클립 목록:\n").append(InputSanitizer.sanitize(clipDescription))
            append("\n\n응답은 clips 배열에 {clipSeq, hookA, hookB} 객체를 담은 JSON으로만 해 줘.")
        }

        val result = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(HookGenerationResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "후킹 문구 응답을 파싱할 수 없습니다")

        val bySeq = result.clips.associateBy { it.clipSeq }
        val hooks = context.clips.flatMap { clip ->
            val generated = bySeq[clip.seq]
                ?: throw BusinessException("AI_PARSE_ERROR", "클립 ${clip.seq}의 후킹 문구가 응답에 없습니다")
            listOf(
                GeneratedHook(clip.id, HookVariant.A, generated.hookA),
                GeneratedHook(clip.id, HookVariant.B, generated.hookB),
            )
        }

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(
                mapOf("clips" to result.clips.map {
                    mapOf("clipSeq" to it.clipSeq, "hookA" to it.hookA, "hookB" to it.hookB)
                }),
            ),
            inputSnapshot = """{"clipCount":${context.clips.size}}""",
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
            hooks = hooks,
        )
    }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 쇼츠 첫 3초를 사로잡는 후킹 문구 전문가입니다. 클립마다 A안과 B안 두 가지를 만듭니다."
    }
}
