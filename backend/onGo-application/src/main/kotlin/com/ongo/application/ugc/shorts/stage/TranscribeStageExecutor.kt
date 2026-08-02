package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.SttUseCase
import com.ongo.domain.ugc.shorts.PipelineStage
import org.springframework.stereotype.Component

/**
 * TRANSCRIBE 단계. SttUseCase.executeInternal을 쓴다 (크레딧은 오케스트레이터가 STT로 관리).
 * 출력 스냅샷: {"text":..., "segments":[{"startMs":..,"endMs":..,"text":..}]}
 */
@Component
class TranscribeStageExecutor(
    private val sttUseCase: SttUseCase,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.TRANSCRIBE

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val result = sttUseCase.executeInternal(context.userId, context.run.sourceVideoId)

        // 초 → ms 변환
        val segments = result.segments.map { segment ->
            TranscriptSegmentMs(
                startMs = (segment.startTime * 1000).toLong(),
                endMs = (segment.endTime * 1000).toLong(),
                text = segment.text,
            )
        }

        val snapshot = mapper.writeValueAsString(
            mapOf(
                "text" to result.text,
                "segments" to segments.map {
                    mapOf("startMs" to it.startMs, "endMs" to it.endMs, "text" to it.text)
                },
            ),
        )

        return ShortsStageOutput(
            outputSnapshot = snapshot,
            inputSnapshot = """{"videoId":${context.run.sourceVideoId}}""",
            transcriptText = result.text,
            transcriptSegments = segments,
        )
    }
}
