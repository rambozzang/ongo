package com.ongo.domain.notification

interface NotificationRepository {
    fun findById(id: Long): Notification?
    fun findByUserId(userId: Long, page: Int, size: Int): List<Notification>
    fun countUnreadByUserId(userId: Long): Int
    fun markAsRead(id: Long)
    fun markAllAsRead(userId: Long)
    fun save(notification: Notification): Notification
    fun delete(id: Long)
}
