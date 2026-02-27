package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.creatornetwork.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.NAME
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.COLLABORATION_REQUESTS
import com.ongo.infrastructure.persistence.jooq.Tables.CREATOR_PROFILES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class CreatorProfileJooqRepository(
    private val dsl: DSLContext,
) : CreatorProfileRepository {

    companion object {
        private val AVATAR_URL = DSL.field("avatar_url", String::class.java)
        private val SUBSCRIBERS = DSL.field("subscribers", Long::class.java)
        private val CATEGORY = DSL.field("category", String::class.java)
        private val MATCH_SCORE = DSL.field("match_score", Int::class.java)
        private val IS_CONNECTED = DSL.field("is_connected", Boolean::class.java)
        private val BIO = DSL.field("bio", String::class.java)
        private val JOINED_AT = DSL.field("joined_at", java.time.LocalDateTime::class.java)
    }

    override fun findByUserId(userId: Long): List<CreatorProfile> =
        dsl.select()
            .from(CREATOR_PROFILES)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findById(id: Long): CreatorProfile? =
        dsl.select()
            .from(CREATOR_PROFILES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByCategory(category: String): List<CreatorProfile> =
        dsl.select()
            .from(CREATOR_PROFILES)
            .where(CATEGORY.eq(category))
            .orderBy(MATCH_SCORE.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(profile: CreatorProfile): CreatorProfile {
        val id = dsl.insertInto(CREATOR_PROFILES)
            .set(USER_ID, profile.userId)
            .set(NAME, profile.name)
            .set(AVATAR_URL, profile.avatarUrl)
            .set(PLATFORM, profile.platform)
            .set(SUBSCRIBERS, profile.subscribers)
            .set(CATEGORY, profile.category)
            .set(MATCH_SCORE, profile.matchScore)
            .set(IS_CONNECTED, profile.isConnected)
            .set(BIO, profile.bio)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    private fun Record.toEntity(): CreatorProfile {
        val joinedAtLdt = localDateTime(JOINED_AT)
        return CreatorProfile(
            id = get(ID),
            userId = get(USER_ID),
            name = get(NAME),
            avatarUrl = get(AVATAR_URL),
            platform = get(PLATFORM) ?: "",
            subscribers = get(SUBSCRIBERS) ?: 0,
            category = get(CATEGORY),
            matchScore = get(MATCH_SCORE) ?: 0,
            isConnected = get(IS_CONNECTED) ?: false,
            bio = get(BIO),
            joinedAt = joinedAtLdt?.atZone(java.time.ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
        )
    }
}

@Repository
class CollaborationRequestJooqRepository(
    private val dsl: DSLContext,
) : CollaborationRequestRepository {

    companion object {
        private val FROM_CREATOR_ID = DSL.field("from_creator_id", Long::class.java)
        private val TO_CREATOR_ID = DSL.field("to_creator_id", Long::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val PROPOSED_TYPE = DSL.field("proposed_type", String::class.java)
    }

    override fun findByCreatorId(creatorId: Long): List<CollaborationRequest> =
        dsl.select()
            .from(COLLABORATION_REQUESTS)
            .where(FROM_CREATOR_ID.eq(creatorId).or(TO_CREATOR_ID.eq(creatorId)))
            .orderBy(Fields.CREATED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findById(id: Long): CollaborationRequest? =
        dsl.select()
            .from(COLLABORATION_REQUESTS)
            .where(Fields.ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun save(request: CollaborationRequest): CollaborationRequest {
        val id = dsl.insertInto(COLLABORATION_REQUESTS)
            .set(FROM_CREATOR_ID, request.fromCreatorId)
            .set(TO_CREATOR_ID, request.toCreatorId)
            .set(MESSAGE, request.message)
            .set(Fields.STATUS, request.status)
            .set(PROPOSED_TYPE, request.proposedType)
            .returningResult(Fields.ID)
            .fetchOne()!!
            .get(Fields.ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        dsl.update(COLLABORATION_REQUESTS)
            .set(Fields.STATUS, status)
            .where(Fields.ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): CollaborationRequest {
        val createdAtLdt = localDateTime(Fields.CREATED_AT)
        return CollaborationRequest(
            id = get(Fields.ID),
            fromCreatorId = get(FROM_CREATOR_ID),
            toCreatorId = get(TO_CREATOR_ID),
            message = get(MESSAGE),
            status = get(Fields.STATUS) ?: "PENDING",
            proposedType = get(PROPOSED_TYPE),
            createdAt = createdAtLdt?.atZone(java.time.ZoneId.systemDefault())?.toInstant() ?: Instant.now(),
        )
    }
}
