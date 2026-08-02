package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit

/**
 * SCHEDULE 단계 (AI 없음). 예약 파라미터로 클립별 scheduledAt을 계산한다.
 * scheduledAt = startAt + (순번) * intervalHours. clip 반영과 SCHEDULED 전이는 오케스트레이터가 한다.
 */
@Component
class ScheduleStageExecutor : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.SCHEDULE

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val schedule = context.schedule
            ?: throw BusinessException("SHORTS_RUN_INVALID_STATE", "예약 파라미터가 없어 예약을 진행할 수 없습니다")

        val targets = context.clips
            .filter { it.status != ClipStatus.DISCARDED }
            .sortedBy { it.seq }

        if (targets.isEmpty()) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "예약할 클립이 없습니다")
        }

        val scheduledAts = targets.mapIndexed { index, clip ->
            clip.id to schedule.startAt.plus(schedule.intervalHours.toLong() * index, ChronoUnit.HOURS)
        }.toMap()

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(
                mapOf(
                    "startAt" to schedule.startAt.toString(),
                    "intervalHours" to schedule.intervalHours,
                    "platforms" to schedule.platforms,
                    "clips" to targets.map {
                        mapOf("clipId" to it.id, "clipSeq" to it.seq, "scheduledAt" to scheduledAts.getValue(it.id).toString())
                    },
                ),
            ),
            inputSnapshot = """{"clipCount":${targets.size},"intervalHours":${schedule.intervalHours}}""",
            scheduledAts = scheduledAts,
        )
    }
}
