package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.approval.ApprovalChain
import com.ongo.domain.approval.ApprovalChainRepository
import com.ongo.infrastructure.persistence.jooq.Fields.APPROVAL_ID
import com.ongo.infrastructure.persistence.jooq.Fields.APPROVED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.APPROVER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.APPROVER_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.COMMENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DEADLINE_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STEP_ORDER
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.APPROVAL_CHAINS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ApprovalChainJooqRepository(
    private val dsl: DSLContext,
) : ApprovalChainRepository {

    override fun findByApprovalId(approvalId: Long): List<ApprovalChain> =
        dsl.select()
            .from(APPROVAL_CHAINS)
            .where(APPROVAL_ID.eq(approvalId))
            .orderBy(STEP_ORDER.asc())
            .fetch()
            .map { it.toApprovalChain() }

    override fun findById(id: Long): ApprovalChain? =
        dsl.select()
            .from(APPROVAL_CHAINS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toApprovalChain()

    override fun findOverdueSteps(): List<ApprovalChain> =
        dsl.select()
            .from(APPROVAL_CHAINS)
            .where(STATUS.eq("PENDING"))
            .and(DEADLINE_AT.lessThan(LocalDateTime.now()))
            .fetch()
            .map { it.toApprovalChain() }

    override fun findApproachingDeadlineSteps(withinMinutes: Long): List<ApprovalChain> {
        val threshold = LocalDateTime.now().plusMinutes(withinMinutes)
        return dsl.select()
            .from(APPROVAL_CHAINS)
            .where(STATUS.eq("PENDING"))
            .and(DEADLINE_AT.lessOrEqual(threshold))
            .and(DEADLINE_AT.greaterThan(LocalDateTime.now()))
            .fetch()
            .map { it.toApprovalChain() }
    }

    override fun save(chain: ApprovalChain): ApprovalChain {
        val record = dsl.insertInto(APPROVAL_CHAINS)
            .set(APPROVAL_ID, chain.approvalId)
            .set(STEP_ORDER, chain.stepOrder)
            .set(APPROVER_ID, chain.approverId)
            .set(APPROVER_NAME, chain.approverName)
            .set(STATUS, chain.status)
            .set(DEADLINE_AT, chain.deadlineAt)
            .set(COMMENT, chain.comment)
            .returning()
            .fetchOne()!!

        return record.toApprovalChain()
    }

    override fun update(chain: ApprovalChain): ApprovalChain {
        val record = dsl.update(APPROVAL_CHAINS)
            .set(STATUS, chain.status)
            .set(APPROVED_AT, chain.approvedAt)
            .set(COMMENT, chain.comment)
            .where(ID.eq(chain.id))
            .returning()
            .fetchOne()!!

        return record.toApprovalChain()
    }

    override fun deleteByApprovalId(approvalId: Long) {
        dsl.deleteFrom(APPROVAL_CHAINS)
            .where(APPROVAL_ID.eq(approvalId))
            .execute()
    }

    private fun Record.toApprovalChain(): ApprovalChain = ApprovalChain(
        id = get(ID),
        approvalId = get(APPROVAL_ID),
        stepOrder = get(STEP_ORDER),
        approverId = get(APPROVER_ID),
        approverName = get(APPROVER_NAME),
        status = get(STATUS),
        deadlineAt = localDateTime(DEADLINE_AT),
        approvedAt = localDateTime(APPROVED_AT),
        comment = get(COMMENT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
