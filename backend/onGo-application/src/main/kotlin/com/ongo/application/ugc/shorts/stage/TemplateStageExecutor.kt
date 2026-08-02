package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * TEMPLATE 단계 (AI). 실행에 지정된 템플릿(또는 워크스페이스 기본 템플릿)을 적용한다.
 * 템플릿 결정은 오케스트레이터가 컨텍스트를 만들 때 하고, 여기서는 AI로 적용 메모를
 * 만들어 스냅샷에 남긴다. REFRAME 크롭의 클립 기록은 오케스트레이터가 한다.
 */
@Component
class TemplateStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.TEMPLATE

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val template = context.template
        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)

        val templateDescription = if (template != null) {
            "템플릿 id=${template.id}, 이름=${template.name}, 배경=${template.backgroundStyle}, " +
                "후킹 폰트=${template.hookFontFamily ?: "기본"}, 자막 폰트=${template.captionFontFamily ?: "기본"}"
        } else {
            "템플릿 없음 (기본 스타일 적용)"
        }

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n적용 대상: ").append(InputSanitizer.sanitize(templateDescription))
            append("\n클립 수: ${context.clips.size}")
            append("\n\n응답은 {summary, notes} JSON으로만 해 줘.")
        }

        val result = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(TemplateApplyResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "템플릿 적용 응답을 파싱할 수 없습니다")

        val snapshot = mapper.writeValueAsString(
            mapOf(
                "templateId" to template?.id,
                "templateName" to template?.name,
                "backgroundStyle" to template?.backgroundStyle,
                "appliedClips" to context.clips.size,
                "summary" to result.summary,
                "notes" to result.notes,
            ),
        )

        return ShortsStageOutput(
            outputSnapshot = snapshot,
            inputSnapshot = """{"templateId":${template?.id ?: "null"},"clipCount":${context.clips.size}}""",
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
        )
    }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 쇼츠 템플릿 적용 전문가입니다. 주어진 템플릿 스타일을 클립에 일관되게 적용하는 방법을 기술합니다."
    }
}
