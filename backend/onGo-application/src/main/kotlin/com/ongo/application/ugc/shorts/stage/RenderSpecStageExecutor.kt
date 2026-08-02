package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.ShortsRenderSpecBuilder
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import org.springframework.stereotype.Component

/**
 * RENDER_SPEC 단계 (AI 없음, 크레딧 0). ShortsRenderSpecBuilder로 클립별
 * render-spec JSON을 만든다. clip.renderSpec 반영과 RENDER_READY 전이는 오케스트레이터가 한다.
 */
@Component
class RenderSpecStageExecutor(
    private val renderSpecBuilder: ShortsRenderSpecBuilder,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.RENDER_SPEC

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val targets = context.clips.filter { it.status != ClipStatus.DISCARDED }
        if (targets.isEmpty()) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "렌더 스펙을 만들 클립이 없습니다")
        }

        val renderSpecs = targets.associate { clip ->
            val selectedHook = context.hooks[clip.id]?.firstOrNull { it.selected }
            val spec = renderSpecBuilder.buildSpec(
                clip = clip,
                sourceVideoId = context.run.sourceVideoId,
                sourceFileUrl = context.sourceFileUrl,
                hook = selectedHook,
                template = context.template,
            )
            clip.id to renderSpecBuilder.toJson(spec)
        }

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(
                mapOf("clips" to targets.map { mapOf("clipId" to it.id, "clipSeq" to it.seq) }),
            ),
            inputSnapshot = """{"clipCount":${targets.size}}""",
            renderSpecs = renderSpecs,
        )
    }
}
