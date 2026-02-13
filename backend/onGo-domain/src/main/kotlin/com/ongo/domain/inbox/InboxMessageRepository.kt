package com.ongo.domain.inbox

interface InboxMessageRepository {
    fun findByUserId(userId: Long, page: Int, size: Int, platform: String?, isRead: Boolean?, messageType: String?): List<InboxMessage>
    fun findById(id: Long): InboxMessage?
    fun countUnreadByUserId(userId: Long): Int
    fun markAsRead(id: Long)
    fun markAllAsRead(userId: Long)
    fun toggleStar(id: Long, starred: Boolean)
    fun save(message: InboxMessage): InboxMessage
    fun countByUserId(userId: Long, platform: String?, isRead: Boolean?, messageType: String?): Long
}
