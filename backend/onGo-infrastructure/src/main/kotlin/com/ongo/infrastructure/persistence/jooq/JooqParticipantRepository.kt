package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.participation.CampaignParticipant
import com.ongo.domain.ugc.participation.ParticipantRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.AGREED_REWARD
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.JOINED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGN_PARTICIPANTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class JooqParticipantRepository(
    private val dsl: DSLContext,
) : ParticipantRepository {

    override fun findById(id: Long): CampaignParticipant? =
        dsl.select().from(UGC_CAMPAIGN_PARTICIPANTS).where(ID.eq(id)).fetchOne()?.toParticipant()

    override fun existsByCampaignIdAndCreatorId(campaignId: Long, creatorId: Long): Boolean =
        dsl.fetchExists(
            dsl.selectOne().from(UGC_CAMPAIGN_PARTICIPANTS)
                .where(CAMPAIGN_ID.eq(campaignId)).and(CREATOR_ID.eq(creatorId)),
        )

    override fun findByCampaignId(campaignId: Long): List<CampaignParticipant> =
        dsl.select().from(UGC_CAMPAIGN_PARTICIPANTS)
            .where(CAMPAIGN_ID.eq(campaignId))
            .orderBy(JOINED_AT.desc())
            .fetch().map { it.toParticipant() }

    override fun findByCreatorId(creatorId: Long, offset: Int, limit: Int): List<CampaignParticipant> =
        dsl.select().from(UGC_CAMPAIGN_PARTICIPANTS)
            .where(CREATOR_ID.eq(creatorId))
            .orderBy(JOINED_AT.desc())
            .limit(limit).offset(offset)
            .fetch().map { it.toParticipant() }

    override fun countByCreatorId(creatorId: Long): Long =
        dsl.selectCount().from(UGC_CAMPAIGN_PARTICIPANTS).where(CREATOR_ID.eq(creatorId))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun save(participant: CampaignParticipant): CampaignParticipant {
        val id = dsl.insertInto(UGC_CAMPAIGN_PARTICIPANTS)
            .set(CAMPAIGN_ID, participant.campaignId)
            .set(CREATOR_ID, participant.creatorId)
            .set(AGREED_REWARD, participant.agreedReward)
            .set(ACTIVE, participant.active)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return dsl.select().from(UGC_CAMPAIGN_PARTICIPANTS).where(ID.eq(id)).fetchOne()!!.toParticipant()
    }

    private fun Record.toParticipant(): CampaignParticipant = CampaignParticipant(
        id = get(ID),
        campaignId = get(CAMPAIGN_ID),
        creatorId = get(CREATOR_ID),
        agreedReward = get(AGREED_REWARD),
        active = get(ACTIVE),
        joinedAt = localDateTime(JOINED_AT),
    )
}
