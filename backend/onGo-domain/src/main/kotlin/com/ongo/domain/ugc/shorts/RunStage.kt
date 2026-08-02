package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 단계 실행 기록. 사용한 프롬프트 개정·AI 제공자·크레딧·입출력 스냅샷을 남겨 추적 가능하게 한다.
 */
data class RunStage(
    val id: Long = 0,
    val runId: Long,
    val stage: PipelineStage,
    val status: RunStageStatus = RunStageStatus.PENDING,
    val promptId: Long? = null,
    val promptRevision: Int? = null,
    val aiProvider: String? = null,
    val creditCost: Int = 0,
    val inputSnapshot: String? = null,
    val outputSnapshot: String? = null,
    val errorMessage: String? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
)
