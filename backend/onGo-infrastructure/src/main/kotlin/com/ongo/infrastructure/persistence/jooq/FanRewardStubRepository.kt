package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.fanreward.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class FanRewardJooqRepository(
    private val dsl: DSLContext,
) : FanRewardRepository {

    companion object {
        private val TABLE = DSL.table("fan_rewards")
        private val NAME = DSL.field("name", String::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val POINTS_COST = DSL.field("points_cost", Int::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val IS_ACTIVE = DSL.field("is_active", Boolean::class.java)
        private val CLAIMED_COUNT = DSL.field("claimed_count", Int::class.java)
        private val MAX_CLAIMS = DSL.field("max_claims", Int::class.java)
        private val IMAGE_URL = DSL.field("image_url", String::class.java)
    }

    override fun findById(id: Long): FanReward? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toReward()

    override fun findByUserId(userId: Long): List<FanReward> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toReward() }

    override fun findByCategory(userId: Long, category: String): List<FanReward> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .and(CATEGORY.eq(category))
            .fetch { it.toReward() }

    override fun save(reward: FanReward): FanReward {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, reward.userId)
            .set(NAME, reward.name)
            .set(DESCRIPTION, reward.description)
            .set(POINTS_COST, reward.pointsCost)
            .set(CATEGORY, reward.category)
            .set(IS_ACTIVE, reward.isActive)
            .set(CLAIMED_COUNT, reward.claimedCount)
            .set(MAX_CLAIMS, reward.maxClaims)
            .set(IMAGE_URL, reward.imageUrl)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun deleteById(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toReward(): FanReward = FanReward(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        description = get(DESCRIPTION),
        pointsCost = get(POINTS_COST) ?: 0,
        category = get(CATEGORY) ?: "BADGE",
        isActive = get(IS_ACTIVE) ?: true,
        claimedCount = get(CLAIMED_COUNT) ?: 0,
        maxClaims = get("max_claims")?.let { (it as Number).toInt() },
        imageUrl = get(IMAGE_URL),
        createdAt = localDateTime(CREATED_AT),
    )
}

@Repository
class FanActivityJooqRepository(
    private val dsl: DSLContext,
) : FanActivityRepository {

    companion object {
        private val TABLE = DSL.table("fan_activities")
        private val FAN_NAME = DSL.field("fan_name", String::class.java)
        private val ACTIVITY_TYPE = DSL.field("activity_type", String::class.java)
        private val POINTS = DSL.field("points", Int::class.java)
        private val DESCRIPTION = DSL.field("description", String::class.java)
        private val TIMESTAMP = DSL.field("timestamp", LocalDateTime::class.java)
    }

    override fun findByUserId(userId: Long): List<FanActivity> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toActivity() }

    override fun save(activity: FanActivity): FanActivity {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, activity.userId)
            .set(FAN_NAME, activity.fanName)
            .set(ACTIVITY_TYPE, activity.activityType)
            .set(POINTS, activity.points)
            .set(DESCRIPTION, activity.description)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()!!.toActivity()
    }

    private fun Record.toActivity(): FanActivity = FanActivity(
        id = get(ID),
        userId = get(USER_ID),
        fanName = get(FAN_NAME),
        activityType = get(ACTIVITY_TYPE),
        points = get(POINTS) ?: 0,
        description = get(DESCRIPTION),
        timestamp = localDateTime(TIMESTAMP),
    )
}
