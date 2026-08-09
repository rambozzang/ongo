package com.ongo.domain.ugc.shorts

/**
 * 파이프라인 실행 상태.
 *
 * AWAITING_HOOK_SELECTION / AWAITING_SCHEDULE 은 사람의 입력을 기다리는 게이트 상태다.
 */
enum class PipelineRunStatus {
    PENDING,
    RUNNING,
    AWAITING_HOOK_SELECTION,
    AWAITING_SCHEDULE,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    CANCELLED,
}
