package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.inbox.InboxMessage
import com.ongo.domain.inbox.InboxMessageRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CONTENT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_READ
import com.ongo.infrastructure.persistence.jooq.Fields.IS_STARRED
import com.ongo.infrastructure.persistence.jooq.Fields.MESSAGE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM
import com.ongo.infrastructure.persistence.jooq.Fields.PLATFORM_MESSAGE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.RECEIVED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.SENDER_AVATAR_URL
import com.ongo.infrastructure.persistence.jooq.Fields.SENDER_NAME
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Tables.INBOX_MESSAGES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class InboxMessageJooqRepository(
    private val dsl: DSLContext,
) : InboxMessageRepository {

    override fun findByUserId(
        userId: Long,
        page: Int,
        size: Int,
        platform: String?,
        isRead: Boolean?,
        messageType: String?,
    ): List<InboxMessage> {
        var condition = USER_ID.eq(userId)
        if (platform != null) condition = condition.and(PLATFORM.eq(platform))
        if (isRead != null) condition = condition.and(IS_READ.eq(isRead))
        if (messageType != null) condition = condition.and(MESSAGE_TYPE.eq(messageType))

        return dsl.select()
            .from(INBOX_MESSAGES)
            .where(condition)
            .orderBy(RECEIVED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toInboxMessage() }
    }

    override fun findById(id: Long): InboxMessage? =
        dsl.select()
            .from(INBOX_MESSAGES)
            .where(ID.eq(id))
            .fetchOne()
            ?.toInboxMessage()

    override fun countUnreadByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(INBOX_MESSAGES)
            .where(USER_ID.eq(userId))
            .and(IS_READ.eq(false))
            .fetchOne(0, Int::class.java) ?: 0

    override fun markAsRead(id: Long) {
        dsl.update(INBOX_MESSAGES)
            .set(IS_READ, true)
            .where(ID.eq(id))
            .execute()
    }

    override fun markAllAsRead(userId: Long) {
        dsl.update(INBOX_MESSAGES)
            .set(IS_READ, true)
            .where(USER_ID.eq(userId))
            .and(IS_READ.eq(false))
            .execute()
    }

    override fun toggleStar(id: Long, starred: Boolean) {
        dsl.update(INBOX_MESSAGES)
            .set(IS_STARRED, starred)
            .where(ID.eq(id))
            .execute()
    }

    override fun save(message: InboxMessage): InboxMessage {
        val record = dsl.insertInto(INBOX_MESSAGES)
            .set(USER_ID, message.userId)
            .set(PLATFORM, message.platform)
            .set(SENDER_NAME, message.senderName)
            .set(SENDER_AVATAR_URL, message.senderAvatarUrl)
            .set(MESSAGE_TYPE, message.messageType)
            .set(CONTENT, message.content)
            .set(IS_READ, message.isRead)
            .set(IS_STARRED, message.isStarred)
            .set(PLATFORM_MESSAGE_ID, message.platformMessageId)
            .set(VIDEO_ID, message.videoId)
            .returning()
            .fetchOne()!!

        return record.toInboxMessage()
    }

    override fun countByUserId(userId: Long, platform: String?, isRead: Boolean?, messageType: String?): Long {
        var condition = USER_ID.eq(userId)
        if (platform != null) condition = condition.and(PLATFORM.eq(platform))
        if (isRead != null) condition = condition.and(IS_READ.eq(isRead))
        if (messageType != null) condition = condition.and(MESSAGE_TYPE.eq(messageType))

        return dsl.select(DSL.count())
            .from(INBOX_MESSAGES)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    private fun Record.toInboxMessage(): InboxMessage = InboxMessage(
        id = get(ID),
        userId = get(USER_ID),
        platform = get(PLATFORM),
        senderName = get(SENDER_NAME),
        senderAvatarUrl = get(SENDER_AVATAR_URL),
        messageType = get(MESSAGE_TYPE),
        content = get(CONTENT),
        isRead = get(IS_READ),
        isStarred = get(IS_STARRED),
        platformMessageId = get(PLATFORM_MESSAGE_ID),
        videoId = get(VIDEO_ID),
        receivedAt = localDateTime(RECEIVED_AT),
        createdAt = localDateTime(CREATED_AT),
    )
}
