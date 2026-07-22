package com.ongo.domain.ugc.submission

import java.time.LocalDateTime

/**
 * 검수 이력. 모든 검수 판단에 검수자·시각·결정·사유를 남긴다.
 * `decision`: APPROVED, CHANGES_REQUESTED, REJECTED.
 */
data class SubmissionReview(
    val id: Long? = null,
    val submissionId: Long,
    val reviewerId: Long,
    val decision: String,
    val comment: String? = null,
    val createdAt: LocalDateTime? = null,
)
