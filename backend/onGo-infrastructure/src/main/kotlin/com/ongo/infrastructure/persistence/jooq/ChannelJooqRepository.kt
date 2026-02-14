package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.infrastructure.persistence.jooq.Fields.ACCESS_TOKEN
import com.ongo.infrastructure.persistence.jooq.Fields.CHANNEL_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.CHANNEL_URL
import com.ongo.infrastructure.persistence.jooq.Fields.CONNECTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_CHANNEL_ID
import com.ongo.infrastructure.persistence.jooq.Fields.PROFILE_IMAGE_URL
import com.ongo.infrastructure.persistence.jooq.Fields.REFRESH_TOKEN
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.SUBSCRIBER_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.TOKEN_EXPIRES_AT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.CHANNELS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class ChannelJooqRepository(
    private val dsl: DSLContext,
) : ChannelRepository {

    override fun findById(id: Long): Channel? =
        dsl.select()
            .from(CHANNELS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toChannel()

    override fun findAllActive(): List<Channel> =
        dsl.select()
            .from(CHANNELS)
            .where(STATUS_TEXT.eq("ACTIVE"))
            .orderBy(CONNECTED_AT.desc())
            .fetch()
            .map { it.toChannel() }

    override fun findByUserId(userId: Long): List<Channel> =
        dsl.select()
            .from(CHANNELS)
            .where(USER_ID.eq(userId))
            .orderBy(CONNECTED_AT.desc())
            .fetch()
            .map { it.toChannel() }

    override fun findByUserIdAndPlatform(userId: Long, platform: Platform): Channel? =
        dsl.select()
            .from(CHANNELS)
            .where(USER_ID.eq(userId))
            .and(PLATFORM_TEXT.eq(platform.name))
            .fetchOne()
            ?.toChannel()

    override fun save(channel: Channel): Channel {
        val id = dsl.insertInto(CHANNELS)
            .set(USER_ID, channel.userId)
            .set(PLATFORM, channel.platform.name)
            .set(PLATFORM_CHANNEL_ID, channel.platformChannelId)
            .set(CHANNEL_NAME, channel.channelName)
            .set(CHANNEL_URL, channel.channelUrl)
            .set(SUBSCRIBER_COUNT, channel.subscriberCount)
            .set(PROFILE_IMAGE_URL, channel.profileImageUrl)
            .set(ACCESS_TOKEN, channel.accessToken)
            .set(REFRESH_TOKEN, channel.refreshToken)
            .set(TOKEN_EXPIRES_AT, channel.tokenExpiresAt)
            .set(STATUS, channel.status)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun update(channel: Channel): Channel {
        dsl.update(CHANNELS)
            .set(CHANNEL_NAME, channel.channelName)
            .set(CHANNEL_URL, channel.channelUrl)
            .set(SUBSCRIBER_COUNT, channel.subscriberCount)
            .set(PROFILE_IMAGE_URL, channel.profileImageUrl)
            .set(ACCESS_TOKEN, channel.accessToken)
            .set(REFRESH_TOKEN, channel.refreshToken)
            .set(TOKEN_EXPIRES_AT, channel.tokenExpiresAt)
            .set(STATUS, channel.status)
            .where(ID.eq(channel.id))
            .execute()

        return findById(channel.id!!)!!
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(CHANNELS)
            .where(ID.eq(id))
            .execute()
    }

    override fun countByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(CHANNELS)
            .where(USER_ID.eq(userId))
            .fetchOne(0, Int::class.java) ?: 0

    private fun Record.toChannel(): Channel {
        val platformStr = get(PLATFORM) ?: "YOUTUBE"
        return Channel(
            id = get(ID),
            userId = get(USER_ID),
            platform = try { Platform.valueOf(platformStr) } catch (_: Exception) { Platform.YOUTUBE },
            platformChannelId = get(PLATFORM_CHANNEL_ID),
            channelName = get(CHANNEL_NAME),
            channelUrl = get(CHANNEL_URL),
            subscriberCount = get(SUBSCRIBER_COUNT) ?: 0,
            profileImageUrl = get(PROFILE_IMAGE_URL),
            accessToken = get(ACCESS_TOKEN),
            refreshToken = get(REFRESH_TOKEN),
            tokenExpiresAt = localDateTime(TOKEN_EXPIRES_AT),
            status = get(STATUS),
            connectedAt = localDateTime(CONNECTED_AT),
            updatedAt = localDateTime(UPDATED_AT),
        )
    }
}
