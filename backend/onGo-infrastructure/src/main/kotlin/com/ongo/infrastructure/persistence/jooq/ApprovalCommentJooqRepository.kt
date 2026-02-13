package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.approval.ApprovalComment
import com.ongo.domain.approval.ApprovalCommentRepository
import com.ongo.infrastructure.persistence.jooq.Fields.APPROVAL_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.FIELD
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_NAME
import com.ongo.infrastructure.persistence.jooq.Tables.APPROVAL_COMMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Repository

@Primary
@Repository
class ApprovalCommentJooqRepository(
    private val dsl: DSLContext,
) : ApprovalCommentRepository {

    override fun findByApprovalId(approvalId: Long): List<ApprovalComment> =
        dsl.select()
            .from(APPROVAL_COMMENTS)
            .where(APPROVAL_ID.eq(approvalId))
            .orderBy(CREATED_AT.asc())
            .fetch()
            .map { it.toApprovalComment() }

    override fun save(comment: ApprovalComment): ApprovalComment {
        val record = dsl.insertInto(APPROVAL_COMMENTS)
            .set(APPROVAL_ID, comment.approvalId)
            .set(USER_ID, comment.userId)
            .set(USER_NAME, comment.userName)
            .set(CONTENT, comment.content)
            .set(FIELD, comment.field)
            .returning()
            .fetchOne()!!

        return record.toApprovalComment()
    }

    private fun Record.toApprovalComment(): ApprovalComment = ApprovalComment(
        id = get(ID),
        approvalId = get(APPROVAL_ID),
        userId = get(USER_ID),
        userName = get(USER_NAME),
        content = get(CONTENT),
        field = get(FIELD),
        createdAt = localDateTime(CREATED_AT),
    )
}
