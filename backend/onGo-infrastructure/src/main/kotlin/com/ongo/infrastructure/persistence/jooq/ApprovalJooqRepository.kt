package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.approval.Approval
import com.ongo.domain.approval.ApprovalRepository
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DECIDED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORMS_STR
import com.ongo.infrastructure.persistence.jooq.Fields.REQUESTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.REQUESTER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REQUESTER_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.REVIEWER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REVIEWER_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.REVISION_NOTE
import com.ongo.infrastructure.persistence.jooq.Fields.SCHEDULED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_TITLE
import com.ongo.infrastructure.persistence.jooq.Tables.APPROVALS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

@Primary
@Repository
class ApprovalJooqRepository(
    private val dsl: DSLContext,
) : ApprovalRepository {

    override fun findById(id: Long): Approval? =
        dsl.select()
            .from(APPROVALS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toApproval()

    override fun findByUserId(userId: Long): List<Approval> =
        dsl.select()
            .from(APPROVALS)
            .where(USER_ID.eq(userId))
            .or(REQUESTER_ID.eq(userId))
            .or(REVIEWER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toApproval() }

    override fun findByReviewerId(reviewerId: Long): List<Approval> =
        dsl.select()
            .from(APPROVALS)
            .where(REVIEWER_ID.eq(reviewerId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toApproval() }

    override fun findByStatus(status: String): List<Approval> =
        dsl.select()
            .from(APPROVALS)
            .where(STATUS.eq(status))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toApproval() }

    override fun save(approval: Approval): Approval {
        val record = dsl.insertInto(APPROVALS)
            .set(USER_ID, approval.userId)
            .set(VIDEO_ID, approval.videoId)
            .set(VIDEO_TITLE, approval.videoTitle)
            .set(PLATFORMS_STR, approval.platforms)
            .set(SCHEDULED_AT, approval.scheduledAt)
            .set(REQUESTER_ID, approval.requesterId)
            .set(REQUESTER_NAME, approval.requesterName)
            .set(REVIEWER_ID, approval.reviewerId)
            .set(REVIEWER_NAME, approval.reviewerName)
            .set(STATUS, approval.status)
            .set(COMMENT, approval.comment)
            .set(REQUESTED_AT, approval.requestedAt)
            .returning()
            .fetchOne()!!

        return record.toApproval()
    }

    override fun update(approval: Approval): Approval {
        val record = dsl.update(APPROVALS)
            .set(STATUS, approval.status)
            .set(COMMENT, approval.comment)
            .set(REVISION_NOTE, approval.revisionNote)
            .set(REVIEWER_ID, approval.reviewerId)
            .set(REVIEWER_NAME, approval.reviewerName)
            .set(DECIDED_AT, approval.decidedAt)
            .where(ID.eq(approval.id))
            .returning()
            .fetchOne()!!

        return record.toApproval()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(APPROVALS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toApproval(): Approval = Approval(
        id = get(ID),
        userId = get(USER_ID),
        videoId = get(VIDEO_ID),
        videoTitle = get(VIDEO_TITLE),
        platforms = get(PLATFORMS_STR),
        scheduledAt = localDateTime(SCHEDULED_AT),
        requesterId = get(REQUESTER_ID),
        requesterName = get(REQUESTER_NAME),
        reviewerId = get(REVIEWER_ID),
        reviewerName = get(REVIEWER_NAME),
        status = get(STATUS),
        comment = get(COMMENT),
        revisionNote = get(REVISION_NOTE),
        requestedAt = localDateTime(REQUESTED_AT) ?: java.time.LocalDateTime.now(),
        decidedAt = localDateTime(DECIDED_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
