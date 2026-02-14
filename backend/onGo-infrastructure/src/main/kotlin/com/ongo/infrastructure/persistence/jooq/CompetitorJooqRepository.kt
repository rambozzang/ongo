package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.infrastructure.persistence.jooq.Fields.AVG_VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.CHANNEL_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CHANNEL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.LAST_SYNCED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_CHANNEL_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PROFILE_IMAGE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.SUBSCRIBER_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.TOTAL_VIEWS
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_COUNT
import com.ongo.infrastructure.persistence.jooq.Tables.COMPETITORS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class CompetitorJooqRepository(
    private val dsl: DSLContext,
) : CompetitorRepository {

    override fun findById(id: Long): Competitor? =
        dsl.select()
            .from(COMPETITORS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toCompetitor()

    override fun findByUserId(userId: Long): List<Competitor> =
        dsl.select()
            .from(COMPETITORS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .fetch()
            .map { it.toCompetitor() }

    override fun save(competitor: Competitor): Competitor {
        val id = dsl.insertInto(COMPETITORS)
            .set(USER_ID, competitor.userId)
            .set(PLATFORM, competitor.platform)
            .set(PLATFORM_CHANNEL_ID, competitor.platformChannelId)
            .set(CHANNEL_NAME, competitor.channelName)
            .set(CHANNEL_URL, competitor.channelUrl)
            .set(SUBSCRIBER_COUNT, competitor.subscriberCount)
            .set(TOTAL_VIEWS, competitor.totalViews)
            .set(VIDEO_COUNT, competitor.videoCount)
            .set(AVG_VIEWS, competitor.avgViews)
            .set(PROFILE_IMAGE_URL, competitor.profileImageUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(competitor: Competitor): Competitor {
        dsl.update(COMPETITORS)
            .set(CHANNEL_NAME, competitor.channelName)
            .set(CHANNEL_URL, competitor.channelUrl)
            .set(SUBSCRIBER_COUNT, competitor.subscriberCount)
            .set(TOTAL_VIEWS, competitor.totalViews)
            .set(VIDEO_COUNT, competitor.videoCount)
            .set(AVG_VIEWS, competitor.avgViews)
            .set(PROFILE_IMAGE_URL, competitor.profileImageUrl)
            .set(LAST_SYNCED_AT, competitor.lastSyncedAt)
            .where(ID.eq(competitor.id))
            .execute()

        return findById(competitor.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(COMPETITORS)
            .where(ID.eq(id))
            .execute()
    }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(COMPETITORS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    private fun Record.toCompetitor(): Competitor = Competitor(
        id = get(ID),
        userId = get(USER_ID),
        platform = get(PLATFORM),
        platformChannelId = get(PLATFORM_CHANNEL_ID),
        channelName = get(CHANNEL_NAME),
        channelUrl = get(CHANNEL_URL),
        subscriberCount = get(SUBSCRIBER_COUNT) ?: 0,
        totalViews = get(TOTAL_VIEWS) ?: 0,
        videoCount = get(VIDEO_COUNT) ?: 0,
        avgViews = get(AVG_VIEWS) ?: 0,
        profileImageUrl = get(PROFILE_IMAGE_URL),
        lastSyncedAt = localDateTime(LAST_SYNCED_AT),
        createdAt = localDateTime(CREATED_AT),
        updatedAt = localDateTime(UPDATED_AT),
    )
}
