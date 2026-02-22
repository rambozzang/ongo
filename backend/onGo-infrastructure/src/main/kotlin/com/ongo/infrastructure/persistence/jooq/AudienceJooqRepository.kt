package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.audience.AudienceProfile
import com.ongo.domain.audience.AudienceRepository
import com.ongo.domain.audience.AudienceSegment
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_UPDATE
import com.ongo.infrastructure.persistence.jooq.Fields.AUTHOR_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.AVATAR_URL
import com.ongo.infrastructure.persistence.jooq.Fields.CONDITIONS
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ENGAGEMENT_SCORE
import com.ongo.infrastructure.persistence.jooq.Fields.FIRST_SEEN_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_SEEN_AT
import com.ongo.infrastructure.persistence.jooq.Fields.MEMBER_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.POSITIVE_RATIO
import com.ongo.infrastructure.persistence.jooq.Fields.TAGS_JSONB
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_INTERACTIONS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.AUDIENCE_PROFILES
import com.ongo.infrastructure.persistence.jooq.Tables.AUDIENCE_SEGMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class AudienceJooqRepository(
    private val dsl: DSLContext,
) : AudienceRepository {

    override fun findProfilesByUserId(userId: Long, sortBy: String, limit: Int, offset: Int): List<AudienceProfile> {
        val orderField = when (sortBy) {
            "total_interactions" -> TOTAL_INTERACTIONS.desc()
            "last_seen_at" -> LAST_SEEN_AT.desc()
            else -> ENGAGEMENT_SCORE.desc()
        }
        return dsl.select().from(AUDIENCE_PROFILES)
            .where(USER_ID.eq(userId))
            .orderBy(orderField)
            .limit(limit).offset(offset)
            .fetch().map { it.toProfile() }
    }

    override fun findProfileById(id: Long): AudienceProfile? =
        dsl.select().from(AUDIENCE_PROFILES).where(ID.eq(id)).fetchOne()?.toProfile()

    override fun countProfilesByUserId(userId: Long): Long =
        dsl.selectCount().from(AUDIENCE_PROFILES).where(USER_ID.eq(userId)).fetchOne(0, Long::class.java) ?: 0L

    override fun saveProfile(profile: AudienceProfile): AudienceProfile {
        val id = dsl.insertInto(AUDIENCE_PROFILES)
            .set(USER_ID, profile.userId)
            .set(AUTHOR_NAME, profile.authorName)
            .set(PLATFORM, profile.platform)
            .set(AVATAR_URL, profile.avatarUrl)
            .set(ENGAGEMENT_SCORE, profile.engagementScore)
            .set(TAGS_JSONB, profile.tags)
            .set(TOTAL_INTERACTIONS, profile.totalInteractions)
            .set(POSITIVE_RATIO, profile.positiveRatio)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findProfileById(id)!!
    }

    override fun upsertProfile(profile: AudienceProfile): AudienceProfile {
        dsl.insertInto(AUDIENCE_PROFILES)
            .set(USER_ID, profile.userId)
            .set(AUTHOR_NAME, profile.authorName)
            .set(PLATFORM, profile.platform)
            .set(AVATAR_URL, profile.avatarUrl)
            .set(ENGAGEMENT_SCORE, profile.engagementScore)
            .set(TOTAL_INTERACTIONS, profile.totalInteractions)
            .set(POSITIVE_RATIO, profile.positiveRatio)
            .set(LAST_SEEN_AT, profile.lastSeenAt)
            .onConflict(USER_ID, AUTHOR_NAME, PLATFORM)
            .doUpdate()
            .set(AVATAR_URL, profile.avatarUrl)
            .set(ENGAGEMENT_SCORE, profile.engagementScore)
            .set(TOTAL_INTERACTIONS, profile.totalInteractions)
            .set(POSITIVE_RATIO, profile.positiveRatio)
            .set(LAST_SEEN_AT, profile.lastSeenAt)
            .set(UPDATED_AT, java.time.LocalDateTime.now())
            .execute()
        return dsl.select().from(AUDIENCE_PROFILES)
            .where(USER_ID.eq(profile.userId))
            .and(AUTHOR_NAME.eq(profile.authorName))
            .and(PLATFORM.eq(profile.platform))
            .fetchOne()!!.toProfile()
    }

    override fun updateProfileTags(id: Long, tags: String) {
        dsl.update(AUDIENCE_PROFILES).set(TAGS_JSONB, tags).where(ID.eq(id)).execute()
    }

    // Segments

    override fun findSegmentsByUserId(userId: Long): List<AudienceSegment> =
        dsl.select().from(AUDIENCE_SEGMENTS).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toSegment() }

    override fun findSegmentById(id: Long): AudienceSegment? =
        dsl.select().from(AUDIENCE_SEGMENTS).where(ID.eq(id)).fetchOne()?.toSegment()

    override fun saveSegment(segment: AudienceSegment): AudienceSegment {
        val id = dsl.insertInto(AUDIENCE_SEGMENTS)
            .set(USER_ID, segment.userId)
            .set(NAME, segment.name)
            .set(DESCRIPTION, segment.description)
            .set(CONDITIONS, segment.conditions)
            .set(AUTO_UPDATE, segment.autoUpdate)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findSegmentById(id)!!
    }

    override fun updateSegment(id: Long, name: String?, description: String?, conditions: String?) {
        val sets = mutableMapOf<org.jooq.Field<*>, Any?>()
        if (name != null) sets[NAME] = name
        if (description != null) sets[DESCRIPTION] = description
        if (conditions != null) sets[CONDITIONS] = conditions
        if (sets.isEmpty()) return
        dsl.update(AUDIENCE_SEGMENTS).set(sets).where(ID.eq(id)).execute()
    }

    override fun deleteSegment(id: Long) {
        dsl.deleteFrom(AUDIENCE_SEGMENTS).where(ID.eq(id)).execute()
    }

    private fun Record.toProfile() = AudienceProfile(
        id = get(ID),
        userId = get(USER_ID),
        authorName = get(AUTHOR_NAME),
        platform = get(PLATFORM),
        avatarUrl = get(AVATAR_URL),
        engagementScore = get(ENGAGEMENT_SCORE),
        tags = get(TAGS_JSONB),
        totalInteractions = get(TOTAL_INTERACTIONS),
        positiveRatio = get(POSITIVE_RATIO),
        firstSeenAt = localDateTime(FIRST_SEEN_AT),
        lastSeenAt = localDateTime(LAST_SEEN_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )

    private fun Record.toSegment() = AudienceSegment(
        id = get(ID),
        userId = get(USER_ID),
        name = get(NAME),
        description = get(DESCRIPTION),
        conditions = get(CONDITIONS),
        autoUpdate = get(AUTO_UPDATE),
        memberCount = get(MEMBER_COUNT),
        createdAt = localDateTime(CREATED_AT),
    )
}
