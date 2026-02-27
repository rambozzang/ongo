package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.livestream.*
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.DESCRIPTION
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.LIVE_STREAMS
import com.ongo.infrastructure.persistence.jooq.Tables.STREAM_CHATS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class LiveStreamJooqRepository(
    private val dsl: DSLContext,
) : LiveStreamRepository {

    companion object {
        private val SCHEDULED_AT = DSL.field("scheduled_at", java.time.LocalDateTime::class.java)
        private val STARTED_AT = DSL.field("started_at", java.time.LocalDateTime::class.java)
        private val ENDED_AT = DSL.field("ended_at", java.time.LocalDateTime::class.java)
        private val VIEWER_COUNT = DSL.field("viewer_count", Int::class.java)
        private val PEAK_VIEWERS = DSL.field("peak_viewers", Int::class.java)
        private val CHAT_MESSAGES = DSL.field("chat_messages", Int::class.java)
        private val STREAM_URL = DSL.field("stream_url", String::class.java)
        private val THUMBNAIL_URL = DSL.field("thumbnail_url", String::class.java)
    }

    override fun findById(id: Long): LiveStream? =
        dsl.select()
            .from(LIVE_STREAMS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toEntity()

    override fun findByUserId(userId: Long): List<LiveStream> =
        dsl.select()
            .from(LIVE_STREAMS)
            .where(USER_ID.eq(userId))
            .orderBy(SCHEDULED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun findByStatus(userId: Long, status: String): List<LiveStream> =
        dsl.select()
            .from(LIVE_STREAMS)
            .where(USER_ID.eq(userId))
            .and(STATUS.eq(status))
            .orderBy(SCHEDULED_AT.desc())
            .fetch()
            .map { it.toEntity() }

    override fun save(stream: LiveStream): LiveStream {
        val id = dsl.insertInto(LIVE_STREAMS)
            .set(USER_ID, stream.userId)
            .set(TITLE, stream.title)
            .set(DESCRIPTION, stream.description)
            .set(PLATFORM, stream.platform)
            .set(STATUS, stream.status)
            .set(SCHEDULED_AT, stream.scheduledAt)
            .set(STARTED_AT, stream.startedAt)
            .set(ENDED_AT, stream.endedAt)
            .set(VIEWER_COUNT, stream.viewerCount)
            .set(PEAK_VIEWERS, stream.peakViewers)
            .set(CHAT_MESSAGES, stream.chatMessages)
            .set(STREAM_URL, stream.streamUrl)
            .set(THUMBNAIL_URL, stream.thumbnailUrl)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String) {
        val updateStep = dsl.update(LIVE_STREAMS)
            .set(STATUS, status)
        if (status == "ENDED") {
            updateStep.set(ENDED_AT, java.time.LocalDateTime.now())
        }
        updateStep.where(ID.eq(id))
            .execute()
    }

    private fun Record.toEntity(): LiveStream = LiveStream(
        id = get(ID),
        userId = get(USER_ID),
        title = get(TITLE) ?: "",
        description = get(DESCRIPTION),
        platform = get(PLATFORM) ?: "",
        status = get(STATUS) ?: "SCHEDULED",
        scheduledAt = localDateTime(SCHEDULED_AT) ?: java.time.LocalDateTime.now(),
        startedAt = localDateTime(STARTED_AT),
        endedAt = localDateTime(ENDED_AT),
        viewerCount = get(VIEWER_COUNT) ?: 0,
        peakViewers = get(PEAK_VIEWERS) ?: 0,
        chatMessages = get(CHAT_MESSAGES) ?: 0,
        streamUrl = get(STREAM_URL),
        thumbnailUrl = get(THUMBNAIL_URL),
    )
}

@Repository
class StreamChatJooqRepository(
    private val dsl: DSLContext,
) : StreamChatRepository {

    companion object {
        private val STREAM_ID = DSL.field("stream_id", Long::class.java)
        private val USERNAME = DSL.field("username", String::class.java)
        private val MESSAGE = DSL.field("message", String::class.java)
        private val TIMESTAMP = DSL.field("timestamp", java.time.LocalDateTime::class.java)
        private val IS_HIGHLIGHTED = DSL.field("is_highlighted", Boolean::class.java)
        private val IS_MODERATOR = DSL.field("is_moderator", Boolean::class.java)
    }

    override fun findByStreamId(streamId: Long): List<StreamChat> =
        dsl.select()
            .from(STREAM_CHATS)
            .where(STREAM_ID.eq(streamId))
            .orderBy(TIMESTAMP.asc())
            .fetch()
            .map { it.toEntity() }

    override fun save(chat: StreamChat): StreamChat {
        val id = dsl.insertInto(STREAM_CHATS)
            .set(STREAM_ID, chat.streamId)
            .set(USERNAME, chat.username)
            .set(MESSAGE, chat.message)
            .set(IS_HIGHLIGHTED, chat.isHighlighted)
            .set(IS_MODERATOR, chat.isModerator)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(STREAM_CHATS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toEntity()
    }

    private fun Record.toEntity(): StreamChat = StreamChat(
        id = get(ID),
        streamId = get(STREAM_ID),
        username = get(USERNAME) ?: "",
        message = get(MESSAGE) ?: "",
        timestamp = localDateTime(TIMESTAMP),
        isHighlighted = get(IS_HIGHLIGHTED) ?: false,
        isModerator = get(IS_MODERATOR) ?: false,
    )
}
