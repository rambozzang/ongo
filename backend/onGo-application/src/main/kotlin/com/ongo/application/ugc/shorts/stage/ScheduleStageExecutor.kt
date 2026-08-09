package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ClipPublication
import com.ongo.domain.ugc.shorts.ClipPublicationRepository
import com.ongo.domain.ugc.shorts.ClipPublicationStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.application.ugc.shorts.ShortsPublishAdapter
import com.ongo.application.ugc.shorts.ShortsPublishRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * SCHEDULE 단계 (AI 없음). 예약 시각을 계산하고 렌더 영상을 기존 게시 흐름에 위임한다.
 *
 * 의존성은 필수다. 예전에는 nullable 이었는데, 주입이 빠지면 예약을 걸어도 아무것도 게시되지
 * 않으면서 오류도 나지 않는 경로가 생겨 필수로 바꿨다.
 * 게시 없이 예약 시각만 확정하고 싶으면 [ScheduleParams.platforms] 를 비워 보낸다.
 */
@Component
class ScheduleStageExecutor(
    private val shortsPublishAdapter: ShortsPublishAdapter,
    private val publicationRepository: ClipPublicationRepository,
) : ShortsStageExecutor {

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

        val plannedAts = targets.mapIndexed { index, clip ->
            clip.id to schedule.startAt.plus(schedule.intervalHours.toLong() * index, ChronoUnit.HOURS)
        }.toMap()

        val skipped = mutableMapOf<Long, String>()
        val publications = mutableListOf<Map<String, Any?>>()
        val scheduledAts = mutableMapOf<Long, Instant>()
        var successfulPublications = 0
        var failedPublications = 0

        targets.forEach { clip ->
            val scheduledAt = plannedAts.getValue(clip.id)
            val renderedVideoId = clip.renderedVideoId
            if (schedule.platforms.isEmpty()) {
                // 플랫폼 미지정 = 게시 없이 예약 시각만 확정한다.
                scheduledAts[clip.id] = scheduledAt
                return@forEach
            }
            if (renderedVideoId == null) {
                val reason = "렌더 영상 미연결"
                skipped[clip.id] = reason
                failedPublications += schedule.platforms.size
                schedule.platforms.forEach { platform ->
                    savePublication(
                        ClipPublication(
                            clipId = clip.id,
                            platform = platform,
                            status = ClipPublicationStatus.SKIPPED,
                            scheduledAt = scheduledAt,
                            errorMessage = reason,
                        ),
                    )
                    publications += mapOf("clipId" to clip.id, "platform" to platform, "status" to "SKIPPED", "reason" to reason)
                }
                return@forEach
            }

            var clipScheduled = false
            val pendingPlatforms = schedule.platforms.filter { platform ->
                val previous = publicationRepository.findByClipIdAndPlatform(clip.id, platform)
                if (previous?.status == ClipPublicationStatus.PUBLISHED || previous?.status == ClipPublicationStatus.SCHEDULED) {
                    clipScheduled = true
                    publications += mapOf("clipId" to clip.id, "platform" to platform, "status" to previous.status.name, "duplicate" to true)
                    false
                } else {
                    true
                }
            }

            if (pendingPlatforms.isNotEmpty()) {
                val requests = pendingPlatforms.map {
                    ShortsPublishRequest(it, clip.title, clip.caption, scheduledAt)
                }
                val outcomes = runCatching {
                    shortsPublishAdapter.publishAll(
                        userId = context.userId,
                        videoId = renderedVideoId,
                        requests = requests,
                    )
                }
                pendingPlatforms.forEach { platform ->
                    val result = outcomes.getOrNull()?.firstOrNull { it.platform == platform }
                    val status = if (outcomes.isSuccess && result != null) ClipPublicationStatus.SCHEDULED else ClipPublicationStatus.FAILED
                    val error = outcomes.exceptionOrNull()?.message ?: result?.errorMessage
                    if (status == ClipPublicationStatus.SCHEDULED) clipScheduled = true
                    if (status == ClipPublicationStatus.SCHEDULED) successfulPublications++ else failedPublications++
                    val previous = publicationRepository.findByClipIdAndPlatform(clip.id, platform)
                    savePublication(
                        (previous ?: ClipPublication(clipId = clip.id, platform = platform)).copy(
                            videoUploadId = result?.videoUploadId,
                            status = status,
                            scheduledAt = scheduledAt,
                            errorMessage = error,
                        ),
                    )
                    publications += mapOf("clipId" to clip.id, "platform" to platform, "status" to status.name, "error" to error)
                }
            }
            // 기존에 성공한 publication은 pending 목록에서 제외되므로, 중복 방지로
            // 건너뛴 경우에도 해당 클립/플랫폼은 성공으로 집계한다.
            if (clipScheduled && pendingPlatforms.size < schedule.platforms.size) {
                successfulPublications += schedule.platforms.size - pendingPlatforms.size
            }
            if (clipScheduled) scheduledAts[clip.id] = scheduledAt
        }

        val scheduleOutcome = when {
            schedule.platforms.isEmpty() -> ScheduleOutcome.NONE
            successfulPublications == 0 -> ScheduleOutcome.FAILED
            failedPublications > 0 -> ScheduleOutcome.PARTIAL
            else -> ScheduleOutcome.SUCCESS
        }

        return ShortsStageOutput(
            outputSnapshot = mapper.writeValueAsString(
                mapOf(
                    "startAt" to schedule.startAt.toString(),
                    "intervalHours" to schedule.intervalHours,
                    "platforms" to schedule.platforms,
                    "outcome" to scheduleOutcome.name,
                    "clips" to targets.map {
                        mapOf(
                            "clipId" to it.id,
                            "clipSeq" to it.seq,
                            "scheduledAt" to plannedAts.getValue(it.id).toString(),
                            "skipped" to skipped.containsKey(it.id),
                            "skipReason" to skipped[it.id],
                        )
                    },
                    "publications" to publications,
                ),
            ),
            inputSnapshot = """{"clipCount":${targets.size},"intervalHours":${schedule.intervalHours},"platformCount":${schedule.platforms.size}}""",
            scheduledAts = scheduledAts,
            scheduleOutcome = scheduleOutcome,
        )
    }

    private fun savePublication(publication: ClipPublication) {
        runCatching {
            if (publication.id > 0) {
                publicationRepository.update(publication)
            } else {
                publicationRepository.save(publication)
            }
        }.recoverCatching {
            // 동시 실행으로 유니크 제약에 걸린 경우에도 게시 중복은 상태 가드로 차단한다.
            publicationRepository.findByClipIdAndPlatform(publication.clipId, publication.platform)
        }
    }
}
