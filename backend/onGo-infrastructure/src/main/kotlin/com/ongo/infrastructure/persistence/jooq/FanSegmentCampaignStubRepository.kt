package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fansegmentcampaign.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CAMPAIGN_SEGMENTS
import com.ongo.infrastructure.persistence.jooq.Tables.FAN_CAMPAIGNS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
class FanCampaignJooqRepository(
    private val dsl: DSLContext,
) : FanCampaignRepository {

    companion object {
        private val SEGMENT_ID = DSL.field("segment_id", Long::class.java)
        private val SEGMENT_NAME = DSL.field("segment_name", String::class.java)
        private val CAMPAIGN_TYPE = DSL.field("campaign_type", String::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val TARGET_COUNT = DSL.field("target_count", Int::class.java)
        private val SENT_COUNT = DSL.field("sent_count", Int::class.java)
        private val OPEN_RATE = DSL.field("open_rate", BigDecimal::class.java)
        private val CLICK_RATE = DSL.field("click_rate", BigDecimal::class.java)
        private val SCHEDULED_AT = DSL.field("scheduled_at", java.time.LocalDateTime::class.java)
    }

    override fun findById(id: Long): FanCampaign? =
        dsl.select()
            .from(FAN_CAMPAIGNS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<FanCampaign> =
        dsl.select()
            .from(FAN_CAMPAIGNS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(campaign: FanCampaign): FanCampaign {
        val id = dsl.insertInto(FAN_CAMPAIGNS)
            .set(USER_ID, campaign.userId)
            .set(NAME, campaign.name)
            .set(SEGMENT_ID, campaign.segmentId)
            .set(SEGMENT_NAME, campaign.segmentName)
            .set(CAMPAIGN_TYPE, campaign.campaignType)
            .set(MESSAGE, campaign.message)
            .set(TARGET_COUNT, campaign.targetCount)
            .set(SENT_COUNT, campaign.sentCount)
            .set(OPEN_RATE, campaign.openRate)
            .set(CLICK_RATE, campaign.clickRate)
            .set(STATUS, campaign.status)
            .set(SCHEDULED_AT, campaign.scheduledAt)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(FAN_CAMPAIGNS)
            .set(STATUS, status)
            .where(ID.eq(id))
            .execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(FAN_CAMPAIGNS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): FanCampaign = FanCampaign(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME) ?: "",
        segmentId = get(SEGMENT_ID),
        segmentName = get(SEGMENT_NAME) ?: "",
        campaignType = get(CAMPAIGN_TYPE) ?: "",
        message = get(MESSAGE) ?: "",
        targetCount = get(TARGET_COUNT) ?: 0,
        sentCount = get(SENT_COUNT) ?: 0,
        openRate = get(OPEN_RATE),
        clickRate = get(CLICK_RATE),
        status = get(STATUS) ?: "DRAFT",
        scheduledAt = localDateTime(SCHEDULED_AT),
        createdAt = localDateTime(CREATED_AT),
    )
}

@Repository
class CampaignSegmentJooqRepository(
    private val dsl: DSLContext,
) : CampaignSegmentRepository {

    companion object {
        private val CRITERIA = DSL.field("criteria", String::class.java)
        private val FAN_COUNT = DSL.field("fan_count", Int::class.java)
        private val AVG_ENGAGEMENT = DSL.field("avg_engagement", BigDecimal::class.java)
    }

    override fun findById(id: Long): CampaignSegment? =
        dsl.select()
            .from(CAMPAIGN_SEGMENTS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<CampaignSegment> =
        dsl.select()
            .from(CAMPAIGN_SEGMENTS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(segment: CampaignSegment): CampaignSegment {
        val id = dsl.insertInto(CAMPAIGN_SEGMENTS)
            .set(USER_ID, segment.userId)
            .set(NAME, segment.name)
            .set(CRITERIA, segment.criteria)
            .set(FAN_COUNT, segment.fanCount)
            .set(AVG_ENGAGEMENT, segment.avgEngagement)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    private fun Record.toEntity(): CampaignSegment = CampaignSegment(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME) ?: "",
        criteria = get(CRITERIA) ?: "{}",
        fanCount = get(FAN_COUNT) ?: 0,
        avgEngagement = get(AVG_ENGAGEMENT) ?: BigDecimal.ZERO,
        createdAt = localDateTime(CREATED_AT),
    )
}
