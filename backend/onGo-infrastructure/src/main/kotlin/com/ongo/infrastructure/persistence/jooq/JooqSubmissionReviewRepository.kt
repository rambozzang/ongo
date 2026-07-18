package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.submission.SubmissionReview
import com.ongo.domain.ugc.submission.SubmissionReviewRepository
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DECISION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.REVIEWER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.SUBMISSION_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SUBMISSION_REVIEWS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class JooqSubmissionReviewRepository(
    private val dsl: DSLContext,
) : SubmissionReviewRepository {

    override fun save(review: SubmissionReview): SubmissionReview {
        val id = dsl.insertInto(UGC_SUBMISSION_REVIEWS)
            .set(SUBMISSION_ID, review.submissionId)
            .set(REVIEWER_ID, review.reviewerId)
            .set(DECISION, review.decision)
            .set(COMMENT, review.comment)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return dsl.select().from(UGC_SUBMISSION_REVIEWS).where(ID.eq(id)).fetchOne()!!.toReview()
    }

    override fun findBySubmissionId(submissionId: Long): List<SubmissionReview> =
        dsl.select().from(UGC_SUBMISSION_REVIEWS)
            .where(SUBMISSION_ID.eq(submissionId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toReview() }

    private fun Record.toReview(): SubmissionReview = SubmissionReview(
        id = get(ID),
        submissionId = get(SUBMISSION_ID),
        reviewerId = get(REVIEWER_ID),
        decision = get(DECISION),
        comment = get(COMMENT),
        createdAt = localDateTime(CREATED_AT),
    )
}
