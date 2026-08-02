package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * REFRAME 단계 (AI). 1080x1920 세로 크롭 박스 {x,y,width,height}를 AI로 결정한다.
 * 결과 크롭은 TEMPLATE 단계에서 각 클립에 기록된다.
 */
@Component
class ReframeStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.REFRAME

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n영상 제목: ").append(InputSanitizer.sanitize(context.sourceVideoTitle ?: ""))
            append("\n전사 요약: ").append(
                InputSanitizer.sanitize(context.transcriptText?.take(2000) ?: ""),
            )
            append("\n\n응답은 x, y, width, height 정수 필드를 가진 JSON으로만 해 줘.")
        }

        val result = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(ReframeCropResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "세로 변환 크롭 박스 응답을 파싱할 수 없습니다")

        val cropJson = mapper.writeValueAsString(
            mapOf("x" to result.x, "y" to result.y, "width" to result.width, "height" to result.height),
        )

        return ShortsStageOutput(
            outputSnapshot = """{"crop":$cropJson}""",
            inputSnapshot = """{"videoId":${context.run.sourceVideoId}}""",
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
            cropJson = cropJson,
        )
    }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 롱폼 영상을 9:16 세로 쇼츠로 리프레임하는 전문가입니다. 인물이 중심에 오도록 크롭 박스를 결정합니다."
    }
}
