package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.reward.RewardConfirmation
import com.ongo.domain.ugc.reward.RewardRepository
import com.ongo.domain.ugc.reward.RewardStatus
import com.ongo.infrastructure.persistence.jooq.Fields.BASE_AMOUNT
import com.ongo.infrastructure.persistence.jooq.Fields.BONUS_AMOUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CAMPAIGN_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIRMED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CONFIRMED_BY
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NOTE
import com.ongo.infrastructure.persistence.jooq.Fields.PARTICIPANT_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_AMOUNT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_REWARD_CONFIRMATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqRewardRepository(
    private val dsl: DSLContext,
) : RewardRepository {

    override fun findById(id: Long): RewardConfirmation? =
        dsl.select().from(UGC_REWARD_CONFIRMATIONS).where(ID.eq(id)).fetchOne()?.toReward()

    override fun findByParticipantId(participantId: Long): RewardConfirmation? =
        dsl.select().from(UGC_REWARD_CONFIRMATIONS).where(PARTICIPANT_ID.eq(participantId)).fetchOne()?.toReward()

    override fun findByCampaignId(campaignId: Long): List<RewardConfirmation> =
        dsl.select().from(UGC_REWARD_CONFIRMATIONS)
            .where(CAMPAIGN_ID.eq(campaignId))
            .orderBy(CREATED_AT.asc())
            .fetch().map { it.toReward() }

    override fun sumSettledTotalByCampaign(campaignId: Long): Long =
        dsl.select(org.jooq.impl.DSL.coalesce(org.jooq.impl.DSL.sum(TOTAL_AMOUNT), org.jooq.impl.DSL.value(0)))
            .from(UGC_REWARD_CONFIRMATIONS)
            .where(CAMPAIGN_ID.eq(campaignId))
            .and(STATUS.`in`(RewardStatus.CONFIRMED.name, RewardStatus.PAID_EXTERNALLY.name))
            .fetchOne(0, Long::class.java) ?: 0L

    override fun save(reward: RewardConfirmation): RewardConfirmation {
        val id = dsl.insertInto(UGC_REWARD_CONFIRMATIONS)
            .set(PARTICIPANT_ID, reward.participantId)
            .set(CAMPAIGN_ID, reward.campaignId)
            .set(CREATOR_ID, reward.creatorId)
            .set(BASE_AMOUNT, reward.baseAmount)
            .set(BONUS_AMOUNT, reward.bonusAmount)
            .set(TOTAL_AMOUNT, reward.totalAmount)
            .set(STATUS, reward.status.name)
            .set(NOTE, reward.note)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)
        return findById(id)!!
    }

    override fun update(reward: RewardConfirmation): RewardConfirmation {
        val id = reward.id ?: throw IllegalArgumentException("id가 없는 보상은 수정할 수 없습니다")
        dsl.update(UGC_REWARD_CONFIRMATIONS)
            .set(BASE_AMOUNT, reward.baseAmount)
            .set(BONUS_AMOUNT, reward.bonusAmount)
            .set(TOTAL_AMOUNT, reward.totalAmount)
            .set(STATUS, reward.status.name)
            .set(NOTE, reward.note)
            .set(CONFIRMED_BY, reward.confirmedBy)
            .set(CONFIRMED_AT, if (reward.status == RewardStatus.CONFIRMED && reward.confirmedAt == null) LocalDateTime.now() else reward.confirmedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .execute()
        return findById(id)!!
    }

    private fun Record.toReward(): RewardConfirmation = RewardConfirmation(
        id = get(ID),
        participantId = get(PARTICIPANT_ID),
        campaignId = get(CAMPAIGN_ID),
        creatorId = get(CREATOR_ID),
        baseAmount = get(BASE_AMOUNT),
        bonusAmount = get(BONUS_AMOUNT),
        totalAmount = get(TOTAL_AMOUNT),
        status = RewardStatus.valueOf(get(STATUS)),
        note = get(NOTE),
        confirmedBy = get(CONFIRMED_BY),
        confirmedAt = localDateTime(CONFIRMED_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
