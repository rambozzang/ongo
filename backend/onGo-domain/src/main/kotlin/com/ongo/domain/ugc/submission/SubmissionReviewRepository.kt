package com.ongo.domain.ugc.submission

interface SubmissionReviewRepository {
    fun save(review: SubmissionReview): SubmissionReview

    fun findBySubmissionId(submissionId: Long): List<SubmissionReview>
}
