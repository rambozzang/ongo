package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.participation.ApplicationStatus
import com.ongo.domain.ugc.participation.CampaignApplication
import com.ongo.domain.ugc.participation.ApplicationRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.DECIDED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DECIDED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.PORTFOLIO_URL
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGN_APPLICATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqApplicationRepository(
    private val dsl: DSLContext,
) : ApplicationRepository {

    override fun findById(id: Long): CampaignApplication? =
        dsl.select().from(UGC_CAMPAIGN_APPLICATIONS).where(ID.eq(id)).fetchOne()?.toApplication()

    override fun findByCampaignIdAndCreatorId(campaignId: Long, creatorId: Long): CampaignApplication? =
        dsl.select().from(UGC_CAMPAIGN_APPLICATIONS)
            .where(CAMPAIGN_ID.eq(campaignId)).and(CREATOR_ID.eq(creatorId))
            .fetchOne()?.toApplication()

    override fun findByCampaignId(campaignId: Long, status: String?, offset: Int, limit: Int): List<CampaignApplication> {
        var condition = CAMPAIGN_ID.eq(campaignId)
        if (status != null) condition = condition.and(STATUS.eq(status))
        return dsl.select().from(UGC_CAMPAIGN_APPLICATIONS)
            .where(condition)
            .orderBy(CREATED_AT.desc())
            .limit(limit).offset(offset)
            .fetch().map { it.toApplication() }
    }

    override fun countByCampaignId(campaignId: Long, status: String?): Long {
        var condition = CAMPAIGN_ID.eq(campaignId)
        if (status != null) condition = condition.and(STATUS.eq(status))
        return dsl.selectCount().from(UGC_CAMPAIGN_APPLICATIONS).where(condition).fetchOne(0, Long::class.java) ?: 0L
    }

    override fun findByCreatorId(creatorId: Long, offset: Int, limit: Int): List<CampaignApplication> =
        dsl.select().from(UGC_CAMPAIGN_APPLICATIONS)
            .where(CREATOR_ID.eq(creatorId))
            .orderBy(CREATED_AT.desc())
            .limit(limit).offset(offset)
            .fetch().map { it.toApplication() }

    override fun countByCreatorId(creatorId: Long): Long =
        dsl.selectCount().from(UGC_CAMPAIGN_APPLICATIONS).where(CREATOR_ID.eq(creatorId))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun save(application: CampaignApplication): CampaignApplication {
        val id = dsl.insertInto(UGC_CAMPAIGN_APPLICATIONS)
            .set(CAMPAIGN_ID, application.campaignId)
            .set(CREATOR_ID, application.creatorId)
            .set(MESSAGE, application.message)
            .set(PORTFOLIO_URL, application.portfolioUrl)
            .set(STATUS, application.status.name)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun updateStatus(application: CampaignApplication): CampaignApplication {
        val id = application.id ?: throw IllegalArgumentException("id가 없는 지원은 수정할 수 없습니다")
        dsl.update(UGC_CAMPAIGN_APPLICATIONS)
            .set(STATUS, application.status.name)
            .set(DECIDED_BY, application.decidedBy)
            .set(DECIDED_AT, LocalDateTime.now())
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
        return findById(id)!!
    }

    private fun Record.toApplication(): CampaignApplication = CampaignApplication(
        id = get(ID),
        campaignId = get(CAMPAIGN_ID),
        creatorId = get(CREATOR_ID),
        message = get(MESSAGE),
        portfolioUrl = get(PORTFOLIO_URL),
        status = ApplicationStatus.valueOf(get(STATUS)),
        decidedBy = get(DECIDED_BY),
        decidedAt = localDateTime(DECIDED_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
