package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.SttUseCase
import com.ongo.application.ugc.shorts.CaptionWord
import com.ongo.application.ugc.shorts.ShortsCostLedger
import com.ongo.domain.ugc.shorts.PipelineStage
import org.springframework.stereotype.Component

/**
 * TRANSCRIBE 단계. SttUseCase.executeInternal을 쓴다 (크레딧은 오케스트레이터가 STT로 관리).
 * 출력 스냅샷: {"text":..., "segments":[{"startMs":..,"endMs":..,"text":..}]}
 */
@Component
class TranscribeStageExecutor(
    private val sttUseCase: SttUseCase,
    private val costLedger: ShortsCostLedger? = null,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.TRANSCRIBE

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val result = sttUseCase.executeInternal(
            context.userId,
            context.run.sourceVideoId,
            context.run.sourceDurationMs,
        ) { durationMs, model ->
            costLedger?.recordTranscription(context.run.id, durationMs, model)
        }

        // 초 → ms 변환. 단어는 중심 시각이 들어가는 세그먼트에 붙인다.
        val words = result.words.map {
            CaptionWord((it.startTime * 1000).toLong(), (it.endTime * 1000).toLong(), it.text)
        }
        val segments = result.segments.map { segment ->
            val startMs = (segment.startTime * 1000).toLong()
            val endMs = (segment.endTime * 1000).toLong()
            TranscriptSegmentMs(
                startMs = startMs,
                endMs = endMs,
                text = segment.text,
                words = words.filter { (it.startMs + it.endMs) / 2 in startMs until endMs },
            )
        }

        // 단어는 [시작, 끝, 글자] 배열로 줄여 담는다 — 3시간 영상이면 수만 개라 키 이름만으로도 커진다.
        val snapshot = mapper.writeValueAsString(
            mapOf(
                "text" to result.text,
                "segments" to segments.map {
                    buildMap {
                        put("startMs", it.startMs)
                        put("endMs", it.endMs)
                        put("text", it.text)
                        if (it.words.isNotEmpty()) put("w", it.words.map { w -> listOf(w.startMs, w.endMs, w.text) })
                    }
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
