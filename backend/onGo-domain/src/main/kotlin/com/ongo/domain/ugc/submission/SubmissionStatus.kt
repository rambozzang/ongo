package com.ongo.domain.ugc.submission

/**
 * 제출 상태 머신.
 *
 * ```
 * DRAFT → SUBMITTED → CHANGES_REQUESTED → SUBMITTED → APPROVED | REJECTED
 * APPROVED → PUBLISHING → PUBLISHED | PUBLISH_FAILED   (Sprint 4)
 * ```
 *
 * 상태 전이는 [ContentSubmission]의 도메인 메서드로만 수행하며,
 * 허용되지 않은 전이는 `IllegalStateException`으로 처리한다.
 */
enum class SubmissionStatus {
    DRAFT,
    SUBMITTED,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED,
    PUBLISHING,
    PUBLISHED,
    PUBLISH_FAILED;

    fun canTransitionTo(target: SubmissionStatus): Boolean = target in allowed()

    /** 크리에이터가 내용을 수정할 수 있는 상태(초안/수정요청). */
    fun isEditable(): Boolean = this == DRAFT || this == CHANGES_REQUESTED

    private fun allowed(): Set<SubmissionStatus> = when (this) {
        DRAFT -> setOf(SUBMITTED)
        SUBMITTED -> setOf(CHANGES_REQUESTED, APPROVED, REJECTED)
        CHANGES_REQUESTED -> setOf(SUBMITTED)
        APPROVED -> setOf(PUBLISHING)
        PUBLISHING -> setOf(PUBLISHED, PUBLISH_FAILED)
        PUBLISH_FAILED -> setOf(PUBLISHING)
        REJECTED, PUBLISHED -> emptySet()
    }
}
