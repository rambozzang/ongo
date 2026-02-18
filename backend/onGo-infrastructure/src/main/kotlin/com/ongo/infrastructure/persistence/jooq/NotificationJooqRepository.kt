package com.ongo.infrastructure.persistence.jooq

import com.ongo.common.enums.NotificationType
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IS_READ
import com.ongo.infrastructure.persistence.jooq.Fields.MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.REFERENCE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.REFERENCE_TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.TITLE
import com.ongo.infrastructure.persistence.jooq.Fields.TYPE
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Tables.NOTIFICATIONS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class NotificationJooqRepository(
    private val dsl: DSLContext,
) : NotificationRepository {

    override fun findById(id: Long): Notification? =
        dsl.select()
            .from(NOTIFICATIONS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toNotification()

    override fun findByUserId(userId: Long, page: Int, size: Int): List<Notification> =
        dsl.select()
            .from(NOTIFICATIONS)
            .where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()
            .map { it.toNotification() }

    override fun countUnreadByUserId(userId: Long): Int =
        dsl.selectCount()
            .from(NOTIFICATIONS)
            .where(USER_ID.eq(userId))
            .and(IS_READ.eq(false))
            .fetchOne(0, Int::class.java) ?: 0

    override fun markAsRead(id: Long) {
        dsl.update(NOTIFICATIONS)
            .set(IS_READ, true)
            .where(ID.eq(id))
            .execute()
    }

    override fun markAllAsRead(userId: Long) {
        dsl.update(NOTIFICATIONS)
            .set(IS_READ, true)
            .where(USER_ID.eq(userId))
            .and(IS_READ.eq(false))
            .execute()
    }

    override fun save(notification: Notification): Notification {
        val id = dsl.insertInto(NOTIFICATIONS)
            .set(USER_ID, notification.userId)
            .set(TYPE, notification.type.name)
            .set(TITLE, notification.title)
            .set(MESSAGE, notification.message)
            .set(IS_READ, notification.isRead)
            .set(REFERENCE_TYPE, notification.referenceType)
            .set(REFERENCE_ID, notification.referenceId)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return dsl.select()
            .from(NOTIFICATIONS)
            .where(ID.eq(id))
            .fetchOne()!!
            .toNotification()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(NOTIFICATIONS)
            .where(ID.eq(id))
            .execute()
    }

    private fun Record.toNotification(): Notification {
        val typeStr = get(TYPE) ?: "SYSTEM"
        return Notification(
            id = get(ID),
            userId = get(USER_ID),
            type = try { NotificationType.valueOf(typeStr) } catch (_: Exception) { NotificationType.SYSTEM },
            title = get(TITLE),
            message = get(MESSAGE),
            isRead = get(IS_READ),
            referenceType = get(REFERENCE_TYPE),
            referenceId = get(REFERENCE_ID),
            createdAt = localDateTime(CREATED_AT),
        )
    }
}
