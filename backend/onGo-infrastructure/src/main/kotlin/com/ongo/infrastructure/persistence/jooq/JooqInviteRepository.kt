package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.participation.CampaignInvite
import com.ongo.domain.ugc.participation.InviteRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACTIVE
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.EXPIRES_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.MAX_USES
import com.ongo.infrastructure.persistence.jooq.Fields.TOKEN_HASH
import com.ongo.infrastructure.persistence.jooq.Fields.USED_COUNT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_CAMPAIGN_INVITES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class JooqInviteRepository(
    private val dsl: DSLContext,
) : InviteRepository {

    override fun findByTokenHash(tokenHash: String): CampaignInvite? =
        dsl.select().from(UGC_CAMPAIGN_INVITES).where(TOKEN_HASH.eq(tokenHash)).fetchOne()?.toInvite()

    override fun findByCampaignId(campaignId: Long): List<CampaignInvite> =
        dsl.select().from(UGC_CAMPAIGN_INVITES)
            .where(CAMPAIGN_ID.eq(campaignId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toInvite() }

    override fun save(invite: CampaignInvite): CampaignInvite {
        val id = dsl.insertInto(UGC_CAMPAIGN_INVITES)
            .set(CAMPAIGN_ID, invite.campaignId)
            .set(TOKEN_HASH, invite.tokenHash)
            .set(EXPIRES_AT, invite.expiresAt)
            .set(MAX_USES, invite.maxUses)
            .set(ACTIVE, invite.active)
            .set(CREATED_BY, invite.createdBy)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return dsl.select().from(UGC_CAMPAIGN_INVITES).where(ID.eq(id)).fetchOne()!!.toInvite()
    }

    override fun incrementUsedCount(id: Long) {
        dsl.update(UGC_CAMPAIGN_INVITES)
            .set(USED_COUNT, USED_COUNT.plus(1))
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toInvite(): CampaignInvite = CampaignInvite(
        id = get(ID),
        campaignId = get(CAMPAIGN_ID),
        tokenHash = get(TOKEN_HASH),
        expiresAt = localDateTime(EXPIRES_AT),
        maxUses = get(MAX_USES),
        usedCount = get(USED_COUNT),
        active = get(ACTIVE),
        createdBy = get(CREATED_BY),
        createdAt = localDateTime(CREATED_AT),
    )
}
