package com.ongo.domain.ugc.submission

import java.time.LocalDateTime

/**
 * 콘텐츠 제출. 참여자(캠페인+크리에이터)당 1건이며 revision으로 재제출 이력을 표현한다.
 * 승인 전에는 게시할 수 없고, 상태 전이는 도메인 메서드로만 수행한다.
 */
data class ContentSubmission(
    val id: Long? = null,
    val campaignId: Long,
    val creatorId: Long,
    val revision: Int = 1,
    val caption: String? = null,
    val status: SubmissionStatus = SubmissionStatus.DRAFT,
    val submittedAt: LocalDateTime? = null,
    val approvedAt: LocalDateTime? = null,
    val assets: List<SubmissionAsset> = emptyList(),
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
) {
    fun submit(): ContentSubmission {
        requireTransition(SubmissionStatus.SUBMITTED)
        if (assets.isEmpty()) throw IllegalStateException("첨부물이 없는 제출은 제출할 수 없습니다")
        return copy(status = SubmissionStatus.SUBMITTED)
    }

    fun requestChanges(): ContentSubmission {
        requireTransition(SubmissionStatus.CHANGES_REQUESTED)
        return copy(status = SubmissionStatus.CHANGES_REQUESTED)
    }

    fun approve(): ContentSubmission {
        requireTransition(SubmissionStatus.APPROVED)
        return copy(status = SubmissionStatus.APPROVED)
    }

    fun reject(): ContentSubmission {
        requireTransition(SubmissionStatus.REJECTED)
        return copy(status = SubmissionStatus.REJECTED)
    }

    /** APPROVED → PUBLISHING. 게시 시작 시 호출. */
    fun markPublishing(): ContentSubmission {
        requireTransition(SubmissionStatus.PUBLISHING)
        return copy(status = SubmissionStatus.PUBLISHING)
    }

    /** 크리에이터가 내용을 수정할 수 있는지(초안/수정요청). */
    fun assertEditable() {
        if (!status.isEditable()) {
            throw IllegalStateException("초안 또는 수정요청 상태에서만 수정할 수 있습니다 (현재: $status)")
        }
    }

    private fun requireTransition(target: SubmissionStatus) {
        if (!status.canTransitionTo(target)) {
            throw IllegalStateException("허용되지 않은 제출 상태 전이입니다: $status → $target")
        }
    }
}
