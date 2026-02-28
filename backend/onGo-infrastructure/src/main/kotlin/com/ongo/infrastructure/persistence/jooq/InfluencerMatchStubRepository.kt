package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.influencermatch.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class InfluencerProfileJooqRepository(
    private val dsl: DSLContext,
) : InfluencerProfileRepository {

    companion object {
        private val TABLE = DSL.table("influencer_profiles")
        private val CHANNEL_NAME = DSL.field("channel_name", String::class.java)
        private val PLATFORM = DSL.field("platform", String::class.java)
        private val SUBSCRIBER_COUNT = DSL.field("subscriber_count", Long::class.java)
        private val AVG_VIEWS = DSL.field("avg_views", Long::class.java)
        private val ENGAGEMENT_RATE = DSL.field("engagement_rate", Double::class.java)
        private val CATEGORIES = DSL.field("categories", String::class.java)
        private val AUDIENCE_DEMOGRAPHICS = DSL.field("audience_demographics", String::class.java)
        private val CONTACT_EMAIL = DSL.field("contact_email", String::class.java)
        private val PROFILE_URL = DSL.field("profile_url", String::class.java)
        private val MATCH_SCORE = DSL.field("match_score", Int::class.java)
    }

    override fun findById(id: Long): InfluencerProfile? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toInfluencerProfile()

    override fun findByUserId(userId: Long): List<InfluencerProfile> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toInfluencerProfile() }

    override fun save(profile: InfluencerProfile): InfluencerProfile {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, profile.userId)
            .set(CHANNEL_NAME, profile.channelName)
            .set(PLATFORM, profile.platform)
            .set(SUBSCRIBER_COUNT, profile.subscriberCount)
            .set(AVG_VIEWS, profile.avgViews)
            .set(ENGAGEMENT_RATE, profile.engagementRate)
            .set(CATEGORIES, profile.categories)
            .set(AUDIENCE_DEMOGRAPHICS, DSL.field("?::jsonb", String::class.java, profile.audienceDemographics))
            .set(CONTACT_EMAIL, profile.contactEmail)
            .set(PROFILE_URL, profile.profileUrl)
            .set(MATCH_SCORE, profile.matchScore)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(profile: InfluencerProfile): InfluencerProfile {
        dsl.update(TABLE)
            .set(CHANNEL_NAME, profile.channelName)
            .set(PLATFORM, profile.platform)
            .set(SUBSCRIBER_COUNT, profile.subscriberCount)
            .set(AVG_VIEWS, profile.avgViews)
            .set(ENGAGEMENT_RATE, profile.engagementRate)
            .set(CATEGORIES, profile.categories)
            .set(AUDIENCE_DEMOGRAPHICS, DSL.field("?::jsonb", String::class.java, profile.audienceDemographics))
            .set(CONTACT_EMAIL, profile.contactEmail)
            .set(PROFILE_URL, profile.profileUrl)
            .set(MATCH_SCORE, profile.matchScore)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(profile.id))
            .execute()

        return findById(profile.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toInfluencerProfile(): InfluencerProfile {
        val demographicsRaw = get("audience_demographics")
        return InfluencerProfile(
            id = get(ID),
            userId = get(USER_ID),
            channelName = get(CHANNEL_NAME),
            platform = get(PLATFORM),
            subscriberCount = get(SUBSCRIBER_COUNT) ?: 0,
            avgViews = get(AVG_VIEWS) ?: 0,
            engagementRate = get(ENGAGEMENT_RATE) ?: 0.0,
            categories = get(CATEGORIES) ?: "[]",
            audienceDemographics = when (demographicsRaw) {
                is String -> demographicsRaw
                else -> demographicsRaw?.toString() ?: "{}"
            },
            contactEmail = get(CONTACT_EMAIL),
            profileUrl = get(PROFILE_URL),
            matchScore = get(MATCH_SCORE) ?: 0,
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}

@Repository
class CollabRequestJooqRepository(
    private val dsl: DSLContext,
) : CollabRequestRepository {

    companion object {
        private val TABLE = DSL.table("collab_requests")
        private val INFLUENCER_PROFILE_ID = DSL.field("influencer_profile_id", Long::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val PROPOSED_BUDGET = DSL.field("proposed_budget", Long::class.java)
        private val PROPOSED_TYPE = DSL.field("proposed_type", String::class.java)
        private val RESPONSE_MESSAGE = DSL.field("response_message", String::class.java)
        private val RESPONDED_AT = DSL.field("responded_at", LocalDateTime::class.java)
    }

    override fun findById(id: Long): CollabRequest? =
        dsl.select().from(TABLE)
            .where(ID.eq(id))
            .fetchOne()?.toCollabRequest()

    override fun findByUserId(userId: Long): List<CollabRequest> =
        dsl.select().from(TABLE)
            .where(USER_ID.eq(userId))
            .fetch { it.toCollabRequest() }

    override fun save(request: CollabRequest): CollabRequest {
        val id = dsl.insertInto(TABLE)
            .set(USER_ID, request.userId)
            .set(INFLUENCER_PROFILE_ID, request.influencerProfileId)
            .set(MESSAGE, request.message)
            .set(PROPOSED_BUDGET, request.proposedBudget)
            .set(PROPOSED_TYPE, request.proposedType)
            .set(STATUS, request.status)
            .set(RESPONSE_MESSAGE, request.responseMessage)
            .set(RESPONDED_AT, request.respondedAt)
            .returningResult(ID)
            .fetchOne()!!.get(ID)

        return findById(id)!!
    }

    override fun update(request: CollabRequest): CollabRequest {
        dsl.update(TABLE)
            .set(INFLUENCER_PROFILE_ID, request.influencerProfileId)
            .set(MESSAGE, request.message)
            .set(PROPOSED_BUDGET, request.proposedBudget)
            .set(PROPOSED_TYPE, request.proposedType)
            .set(STATUS, request.status)
            .set(RESPONSE_MESSAGE, request.responseMessage)
            .set(RESPONDED_AT, request.respondedAt)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(request.id))
            .execute()

        return findById(request.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(TABLE).where(ID.eq(id)).execute()
    }

    private fun Record.toCollabRequest(): CollabRequest =
        CollabRequest(
            id = get(ID),
            userId = get(USER_ID),
            influencerProfileId = get(INFLUENCER_PROFILE_ID),
            message = get(MESSAGE),
            proposedBudget = get(PROPOSED_BUDGET) ?: 0,
            proposedType = get(PROPOSED_TYPE) ?: "COLLABORATION",
            status = get(STATUS) ?: "PENDING",
            responseMessage = get(RESPONSE_MESSAGE),
            respondedAt = localDateTime(RESPONDED_AT),
            createdAt = localDateTime(CREATED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
}
