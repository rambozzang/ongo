package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.hashtagstrategy.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class HashtagStrategyJooqRepository(
    private val dsl: DSLContext,
) : HashtagStrategyRepository {

    companion object {
        private val TABLE = DSL.table("hashtag_sets")
        private val NAME = DSL.field("name", String::class.java)
        private val HASHTAGS = DSL.field("hashtags", String::class.java)
        private val TOTAL_REACH_ESTIMATE = DSL.field("total_reach_estimate", Long::class.java)
        private val OVERALL_DIFFICULTY = DSL.field("overall_difficulty", String::class.java)
        private val SCORE = DSL.field("score", Int::class.java)
    }

    override fun findById(id: Long): HashtagSet? =
        dsl.select().from(TABLE).where(ID.eq(id)).fetchOne()?.toHashtagSet()

    override fun findByUserId(userId: Long): List<HashtagSet> =
        dsl.select().from(TABLE).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toHashtagSet() }

    override fun save(hashtagSet: HashtagSet): HashtagSet {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, hashtagSet.userId)
            .set(NAME, hashtagSet.name)
            .set(HASHTAGS, DSL.field("?::jsonb", String::class.java, hashtagSet.hashtags))
            .set(TOTAL_REACH_ESTIMATE, hashtagSet.totalReachEstimate)
            .set(OVERALL_DIFFICULTY, hashtagSet.overallDifficulty)
            .set(SCORE, hashtagSet.score)
            .set(PLATFORM, hashtagSet.platform)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(hashtagSet: HashtagSet): HashtagSet {
        dsl.update(TABLE)
            .set(NAME, hashtagSet.name)
            .set(HASHTAGS, DSL.field("?::jsonb", String::class.java, hashtagSet.hashtags))
            .set(TOTAL_REACH_ESTIMATE, hashtagSet.totalReachEstimate)
            .set(OVERALL_DIFFICULTY, hashtagSet.overallDifficulty)
            .set(SCORE, hashtagSet.score)
            .set(PLATFORM, hashtagSet.platform)
            .where(ID.eq(hashtagSet.id))
            .execute()

        return findById(hashtagSet.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toHashtagSet(): HashtagSet {
        val hashtagsRaw = get("hashtags")
        return HashtagSet(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            hashtags = when (hashtagsRaw) {
                is String -> hashtagsRaw
                else -> hashtagsRaw?.toString() ?: "[]"
            },
            totalReachEstimate = get(TOTAL_REACH_ESTIMATE) ?: 0,
            overallDifficulty = get(OVERALL_DIFFICULTY),
            score = get(SCORE) ?: 0,
            platform = get(PLATFORM),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
