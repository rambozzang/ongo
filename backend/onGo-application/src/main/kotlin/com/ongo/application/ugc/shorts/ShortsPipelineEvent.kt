package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.PipelineStage
import java.time.Instant

/**
 * 쇼츠 파이프라인 실행 이벤트. 커밋 후 @Async 리스너가 오케스트레이터를 돌린다.
 */
data class ShortsPipelineEvent(
    val runId: Long,
    val fromStage: PipelineStage,
    val scheduleStartAt: Instant? = null,
    val scheduleIntervalHours: Int? = null,
    val platforms: List<String> = emptyList(),
)
