package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.campaign.Campaign
import com.ongo.domain.ugc.campaign.CampaignRepository
import com.ongo.domain.ugc.campaign.CampaignStatus
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENCY
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.END_AT
import com.ongo.infrastructure.persistence.jooq.Fields.FIXED_REWARD_PER_CREATOR
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.OBJECTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.START_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_BUDGET
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.VERSION
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGNS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqCampaignRepository(
    private val dsl: DSLContext,
) : CampaignRepository {

    override fun findById(id: Long): Campaign? =
        dsl.select()
            .from(UGC_CAMPAIGNS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toCampaign()

    override fun findByWorkspaceId(
        workspaceId: Long,
        status: String?,
        query: String?,
        offset: Int,
        limit: Int,
    ): List<Campaign> =
        dsl.select()
            .from(UGC_CAMPAIGNS)
            .where(filter(workspaceId, status, query))
            .orderBy(CREATED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map { it.toCampaign() }

    override fun countByWorkspaceId(workspaceId: Long, status: String?, query: String?): Long =
        dsl.selectCount()
            .from(UGC_CAMPAIGNS)
            .where(filter(workspaceId, status, query))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun save(campaign: Campaign): Campaign {
        val id = dsl.insertInto(UGC_CAMPAIGNS)
            .set(WORKSPACE_ID, campaign.workspaceId)
            .set(NAME, campaign.name)
            .set(DESCRIPTION, campaign.description)
            .set(STATUS, campaign.status.name)
            .set(OBJECTIVE, campaign.objective)
            .set(TOTAL_BUDGET, campaign.totalBudget)
            .set(CURRENCY, campaign.currency)
            .set(FIXED_REWARD_PER_CREATOR, campaign.fixedRewardPerCreator)
            .set(START_AT, campaign.startAt)
            .set(END_AT, campaign.endAt)
            .set(CREATED_BY, campaign.createdBy)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(campaign: Campaign): Campaign {
        val id = campaign.id ?: throw IllegalArgumentException("id가 없는 캠페인은 수정할 수 없습니다")

        // 낙관적 락: 로드 시점 version과 일치할 때만 갱신하고 version을 증가시킨다.
        val affected = dsl.update(UGC_CAMPAIGNS)
            .set(NAME, campaign.name)
            .set(DESCRIPTION, campaign.description)
            .set(STATUS, campaign.status.name)
            .set(OBJECTIVE, campaign.objective)
            .set(TOTAL_BUDGET, campaign.totalBudget)
            .set(CURRENCY, campaign.currency)
            .set(FIXED_REWARD_PER_CREATOR, campaign.fixedRewardPerCreator)
            .set(START_AT, campaign.startAt)
            .set(END_AT, campaign.endAt)
            .set(VERSION, campaign.version + 1)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .and(VERSION.eq(campaign.version))
            .execute()

        if (affected == 0) {
            throw IllegalStateException("캠페인이 다른 곳에서 수정되었습니다. 새로고침 후 다시 시도해 주세요")
        }
        return findById(id)!!
    }

    private fun filter(workspaceId: Long, status: String?, query: String?) =
        WORKSPACE_ID.eq(workspaceId).let { base ->
            var c = base
            if (status != null) c = c.and(STATUS.eq(status))
            if (!query.isNullOrBlank()) c = c.and(NAME.likeIgnoreCase("%$query%"))
            c
        }

    private fun Record.toCampaign(): Campaign = Campaign(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        name = get(NAME),
        description = get(DESCRIPTION),
        status = CampaignStatus.valueOf(get(STATUS)),
        objective = get(OBJECTIVE),
        totalBudget = get(TOTAL_BUDGET),
        currency = get(CURRENCY),
        fixedRewardPerCreator = get(FIXED_REWARD_PER_CREATOR),
        startAt = localDateTime(START_AT),
        endAt = localDateTime(END_AT),
        createdBy = get(CREATED_BY),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
        version = get(VERSION),
    )
}
